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

    @Test
    @DisplayName("编辑替换标签：回退旧标签 use_count、删旧关联、清冗余列后再挂新标签")
    void replaceForPostRollsBackOldLinksAndAttachesNew() {
        PostTagDO old = new PostTagDO();
        old.setPostId(POST_ID);
        old.setTagId(99L);
        when(postTagMapper.selectList(any(Wrapper.class))).thenReturn(List.of(old));
        when(tagMapper.selectOne(any(Wrapper.class))).thenReturn(tag(TAG_ID, "Redis"));

        repository.replaceForPost(POST_ID, List.of("Redis"));

        verify(postTagMapper).delete(any(Wrapper.class));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<TagDO>> tagUpdates = ArgumentCaptor.forClass(Wrapper.class);
        verify(tagMapper, times(2)).update(isNull(), tagUpdates.capture());
        List<String> tagSqlSets = tagUpdates.getAllValues().stream()
                .map(w -> ((LambdaUpdateWrapper<TagDO>) w).getSqlSet()).toList();
        assertThat(tagSqlSets).anySatisfy(set -> assertThat(set).contains("GREATEST(use_count - 1, 0)"));
        assertThat(tagSqlSets).anySatisfy(set -> assertThat(set).contains("use_count = use_count + 1"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<PostDO>> postUpdates = ArgumentCaptor.forClass(Wrapper.class);
        verify(postMapper, times(2)).update(isNull(), postUpdates.capture());
        List<LambdaUpdateWrapper<PostDO>> postWrappers = postUpdates.getAllValues().stream()
                .map(w -> (LambdaUpdateWrapper<PostDO>) w).toList();
        // 第一次把冗余列清空（null），第二次由 attachToPost 回写新标签
        assertThat(postWrappers.get(0).getSqlSet()).contains("tags");
        assertThat(postWrappers.get(0).getParamNameValuePairs().values()).containsNull();
        assertThat(postWrappers.get(1).getParamNameValuePairs().values()).contains("Redis");

        verify(postTagMapper).insert(any(PostTagDO.class));
    }

    @Test
    @DisplayName("重命名：改 tags.name，并把受影响帖子的冗余列旧名换成新名（保序）")
    void renameUpdatesTagAndRedundancy() {
        when(tagMapper.selectById(TAG_ID)).thenReturn(tag(TAG_ID, "Java"));
        when(postTagMapper.findPostIdsByTagId(TAG_ID)).thenReturn(List.of(POST_ID));
        when(postMapper.selectById(POST_ID)).thenReturn(postWithTags(POST_ID, "Java,Vue"));

        int affected = repository.renameTag(TAG_ID, "JVM");

        assertThat(affected).isEqualTo(1);
        LambdaUpdateWrapper<TagDO> tagUpdate = capturedTagUpdate();
        assertThat(tagUpdate.getSqlSet()).contains("name");
        assertThat(tagUpdate.getParamNameValuePairs().values()).contains("JVM");
        LambdaUpdateWrapper<PostDO> postUpdate = capturedPostUpdate();
        assertThat(postUpdate.getParamNameValuePairs().values()).contains("JVM,Vue");
    }

    @Test
    @DisplayName("重命名：标签已不存在 → 返回 0，不发任何写")
    void renameMissingTagDoesNothing() {
        when(tagMapper.selectById(TAG_ID)).thenReturn(null);

        assertThat(repository.renameTag(TAG_ID, "JVM")).isZero();

        verify(tagMapper, never()).update(isNull(), any(Wrapper.class));
        verifyNoInteractions(postTagMapper);
    }

    @Test
    @DisplayName("合并：同帖已挂目标时只删源关联；否则把关联改挂目标；冗余列改目标名；use_count 相加；最后删源行")
    void mergeMovesLinksAndMergesCounts() {
        TagDO source = tag(5L, "Java");
        source.setUseCount(3);
        TagDO target = tag(9L, "后端");
        when(tagMapper.selectById(5L)).thenReturn(source);
        when(tagMapper.selectById(9L)).thenReturn(target);
        when(postTagMapper.findPostIdsByTagId(5L)).thenReturn(List.of(7L, 8L));
        when(postTagMapper.selectCount(any(Wrapper.class))).thenReturn(1L, 0L);
        when(postMapper.selectById(7L)).thenReturn(postWithTags(7L, "Java,Vue"));
        when(postMapper.selectById(8L)).thenReturn(postWithTags(8L, "Java,MySQL"));

        int affected = repository.mergeTag(5L, 9L);

        assertThat(affected).isEqualTo(2);
        verify(postTagMapper, times(1)).delete(any(Wrapper.class));
        verify(postTagMapper, times(1)).update(isNull(), any(Wrapper.class));
        assertThat(capturedTagUpdate().getSqlSet()).contains("use_count = use_count +");
        assertThat(capturedTagUpdate().getParamNameValuePairs().values()).contains(3);
        verify(tagMapper).deleteById(5L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<PostDO>> postUpdates = ArgumentCaptor.forClass(Wrapper.class);
        verify(postMapper, times(2)).update(isNull(), postUpdates.capture());
        List<List<String>> writtenTags = postUpdates.getAllValues().stream()
                .map(w -> {
                    w.getSqlSegment();
                    return ((LambdaUpdateWrapper<PostDO>) w).getParamNameValuePairs().values().stream()
                            .map(String::valueOf)
                            .toList();
                })
                .toList();
        assertThat(writtenTags.get(0)).contains("后端,Vue");
        assertThat(writtenTags.get(1)).contains("后端,MySQL");
    }

    @Test
    @DisplayName("合并：源或目标已不存在 → 返回 0，不发任何写")
    void mergeMissingTagDoesNothing() {
        when(tagMapper.selectById(5L)).thenReturn(null);
        when(tagMapper.selectById(9L)).thenReturn(tag(9L, "后端"));

        assertThat(repository.mergeTag(5L, 9L)).isZero();

        verify(tagMapper, never()).deleteById(5L);
        verifyNoInteractions(postTagMapper);
    }

    // ---------- helpers ----------

    private static PostDO postWithTags(long id, String tags) {
        PostDO p = new PostDO();
        p.setId(id);
        p.setTags(tags);
        return p;
    }

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
