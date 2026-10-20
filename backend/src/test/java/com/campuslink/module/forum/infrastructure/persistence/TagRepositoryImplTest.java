package com.campuslink.module.forum.infrastructure.persistence;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.campuslink.module.forum.infrastructure.persistence.mapper.PostMapper;
import com.campuslink.module.forum.infrastructure.persistence.mapper.PostTagMapper;
import com.campuslink.module.forum.infrastructure.persistence.mapper.TagMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@code TagRepositoryImpl#attachToPost} 的 find-or-create + 关联 + 冗余列回写（CR-083）。
 *
 * <p>守四件事：① 空标签列表**一条 SQL 都不发**（发帖正文可以没有标签）；
 * ② 并发建同名标签撞唯一键时回读既有行，不把冲突抛给发帖主流程；
 * ③ 同一帖同一标签重复挂载被唯一键吞掉后，{@code use_count} **仍然只加一次**；
 * ④ {@code posts.tags} 冗余列与本次标签列表逐字一致（逗号分隔、保输入顺序）。
 *
 * <p>本类不证明数据库行为：全部断言落在 wrapper 的
 * {@code getSqlSet()} / {@code getSqlParamNameValuePairs()} 与 mock 收到的参数上。
 */
@ExtendWith(MockitoExtension.class)
class TagRepositoryImplTest {

    private static final long POST_ID = 7L;
    private static final long TAG_ID = 5L;

    @Mock
    private TagMapper tagMapper;
    @Mock
    private PostTagMapper postTagMapper;
    @Mock
    private PostMapper postMapper;

    private TagRepositoryImpl repository;

    @BeforeAll
    static void initTableInfoCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(TagMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, TagDO.class);
        TableInfoHelper.initTableInfo(assistant, PostTagDO.class);
        TableInfoHelper.initTableInfo(assistant, PostDO.class);
    }

    @BeforeEach
    void setUp() {
        repository = new TagRepositoryImpl(tagMapper, postTagMapper, postMapper);
    }

    @Test
    @DisplayName("空 / null 标签列表：直接返回，不发任何 SQL（帖子不必带标签）")
    void emptyNamesSendNoSql() {
        repository.attachToPost(POST_ID, List.of());
        repository.attachToPost(POST_ID, null);

        verifyNoInteractions(tagMapper, postTagMapper, postMapper);
    }

    @Test
    @DisplayName("已存在的标签：不建标签，挂关联 + use_count 自增 + posts.tags 回写")
    void existingTagOnlyLinksAndBumpsCount() {
        when(tagMapper.selectOne(any(Wrapper.class))).thenReturn(tag(TAG_ID, "Java"));

        repository.attachToPost(POST_ID, List.of("Java"));

        verify(tagMapper, never()).insert(any(TagDO.class));
        ArgumentCaptor<PostTagDO> link = ArgumentCaptor.forClass(PostTagDO.class);
        verify(postTagMapper).insert(link.capture());
        assertThat(link.getValue().getPostId()).isEqualTo(POST_ID);
        assertThat(link.getValue().getTagId()).isEqualTo(TAG_ID);

        assertThat(capturedTagUpdate().getSqlSet()).contains("use_count = use_count + 1");
        assertThat(capturedTagUpdate().getParamNameValuePairs().values()).contains(TAG_ID);

        LambdaUpdateWrapper<PostDO> postUpdate = capturedPostUpdate();
        assertThat(postUpdate.getSqlSet()).contains("tags");
        assertThat(postUpdate.getParamNameValuePairs().values()).contains("Java");
    }

    @Test
    @DisplayName("新标签：insert 后回读 id 再挂关联（id 由库自增回填）")
    void newTagIsInsertedThenLinked() {
        when(tagMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(tagMapper.insert(any(TagDO.class))).thenAnswer(inv -> {
            inv.getArgument(0, TagDO.class).setId(TAG_ID);
            return 1;
        });

        repository.attachToPost(POST_ID, List.of("MySQL"));

        ArgumentCaptor<TagDO> inserted = ArgumentCaptor.forClass(TagDO.class);
        verify(tagMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getName()).isEqualTo("MySQL");
        assertThat(inserted.getValue().getUseCount()).isZero();

        ArgumentCaptor<PostTagDO> link = ArgumentCaptor.forClass(PostTagDO.class);
        verify(postTagMapper).insert(link.capture());
        assertThat(link.getValue().getTagId()).isEqualTo(TAG_ID);
    }

    @Test
    @DisplayName("并发建同名标签：唯一键冲突被吞掉并回读既有行，发帖主流程不受影响")
    void concurrentTagCreationFallsBackToExistingRow() {
        when(tagMapper.selectOne(any(Wrapper.class))).thenReturn(null, tag(TAG_ID, "Redis"));
        when(tagMapper.insert(any(TagDO.class)))
                .thenThrow(new DuplicateKeyException("Duplicate entry 'Redis' for key 'tags.uk_tags_name'"));

        assertThatCode(() -> repository.attachToPost(POST_ID, List.of("Redis")))
                .doesNotThrowAnyException();

        verify(tagMapper, times(2)).selectOne(any(Wrapper.class));
        ArgumentCaptor<PostTagDO> link = ArgumentCaptor.forClass(PostTagDO.class);
        verify(postTagMapper).insert(link.capture());
        assertThat(link.getValue().getTagId()).isEqualTo(TAG_ID);
    }

    @Test
    @DisplayName("重复挂载同一标签：post_tag 唯一键冲突被吞，use_count 仍按本次调用自增一次")
    void duplicatePostTagInsertIsIdempotent() {
        when(tagMapper.selectOne(any(Wrapper.class))).thenReturn(tag(TAG_ID, "Java"));
        when(postTagMapper.insert(any(PostTagDO.class)))
                .thenThrow(new DuplicateKeyException("Duplicate entry '7-5' for key 'post_tags.PRIMARY'"));

        assertThatCode(() -> repository.attachToPost(POST_ID, List.of("Java")))
                .doesNotThrowAnyException();

        assertThat(capturedTagUpdate().getSqlSet()).contains("use_count = use_count + 1");
    }

    @Test
    @DisplayName("多个标签：posts.tags 按输入顺序逗号拼接，每个标签各自增一次")
    void multipleTagsAreJoinedInInputOrder() {
        when(tagMapper.selectOne(any(Wrapper.class)))
                .thenReturn(tag(1L, "Java"), tag(2L, "MySQL"));

        repository.attachToPost(POST_ID, List.of("Java", "MySQL"));

        verify(tagMapper, times(2)).update(isNull(), any(Wrapper.class));
        verify(postTagMapper, times(2)).insert(any(PostTagDO.class));
        assertThat(capturedPostUpdate().getParamNameValuePairs().values()).contains("Java,MySQL");
    }

    // ---------- helpers ----------

    private static TagDO tag(long id, String name) {
        TagDO t = new TagDO();
        t.setId(id);
        t.setName(name);
        t.setUseCount(0);
        return t;
    }

    @SuppressWarnings("unchecked")
    private LambdaUpdateWrapper<TagDO> capturedTagUpdate() {
        ArgumentCaptor<Wrapper<TagDO>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(tagMapper, org.mockito.Mockito.atLeastOnce()).update(isNull(), captor.capture());
        LambdaUpdateWrapper<TagDO> wrapper = (LambdaUpdateWrapper<TagDO>) captor.getValue();
        // eq 的绑定值是渲染 WHERE 时才写入 paramNameValuePairs 的，先触发一次再读
        wrapper.getSqlSegment();
        return wrapper;
    }

    @SuppressWarnings("unchecked")
    private LambdaUpdateWrapper<PostDO> capturedPostUpdate() {
        ArgumentCaptor<Wrapper<PostDO>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(postMapper).update(isNull(), captor.capture());
        LambdaUpdateWrapper<PostDO> wrapper = (LambdaUpdateWrapper<PostDO>) captor.getValue();
        // eq 的绑定值是渲染 WHERE 时才写入 paramNameValuePairs 的，先触发一次再读
        wrapper.getSqlSegment();
        return wrapper;
    }
}
