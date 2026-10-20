package com.campuslink.module.forum.infrastructure.persistence;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campuslink.module.forum.domain.gateway.PageResult;
import com.campuslink.module.forum.domain.model.BoardType;
import com.campuslink.module.forum.domain.model.Post;
import com.campuslink.module.forum.domain.model.PostStatus;
import com.campuslink.module.forum.infrastructure.persistence.mapper.FavoriteMapper;
import com.campuslink.module.forum.infrastructure.persistence.mapper.PostMapper;
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

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code FavoriteRepositoryImpl} 的 toggle / 收藏页顺序 / 批量回读（F-FORUM-005 / 006，CR-111 覆盖率批）。
 *
 * <p>与既有的 {@code FavoriteRepositoryImplTombstoneTest} 分工：那支只守"读帖子必须排墓碑"这一条
 * （且已把两侧绑定值逐列比对）；本类补 toggle 的幂等语义、收藏页**按收藏时间回填而不按帖子表顺序**、
 * 不可见帖从页内剔除但 total 仍按收藏行数、以及批量回读的空集合短路。
 *
 * <p>本类不证明数据库执行结果：断言落在 wrapper 条件与返回映射上。
 */
@ExtendWith(MockitoExtension.class)
class FavoriteRepositoryImplTest {

    private static final long USER_ID = 32L;
    private static final long FIRST_POST_ID = 24L;
    private static final long SECOND_POST_ID = 25L;
    private static final Instant CREATED_AT = Instant.parse("2026-10-15T08:00:00Z");

    @Mock
    private FavoriteMapper favoriteMapper;
    @Mock
    private PostMapper postMapper;

    private FavoriteRepositoryImpl repository;

    @BeforeAll
    static void initTableInfoCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(FavoriteMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, FavoriteDO.class);
        TableInfoHelper.initTableInfo(assistant, PostDO.class);
    }

    @BeforeEach
    void setUp() {
        repository = new FavoriteRepositoryImpl(favoriteMapper, postMapper);
    }

    @Test
    @DisplayName("首次收藏：insert 一行 (user, post) 并返回 true")
    void firstToggleInserts() {
        assertThat(repository.toggle(USER_ID, FIRST_POST_ID)).isTrue();

        ArgumentCaptor<FavoriteDO> captor = ArgumentCaptor.forClass(FavoriteDO.class);
        verify(favoriteMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getPostId()).isEqualTo(FIRST_POST_ID);
    }

    @Test
    @DisplayName("重复收藏（唯一键冲突）：转成取消，DELETE 双条件齐全并返回 false")
    void duplicateToggleDeletesAndReturnsFalse() {
        when(favoriteMapper.insert(any(FavoriteDO.class)))
                .thenThrow(new DuplicateKeyException("Duplicate entry '32-24' for key 'favorites.PRIMARY'"));

        assertThat(repository.toggle(USER_ID, FIRST_POST_ID)).isFalse();

        LambdaQueryWrapper<FavoriteDO> query = capturedDelete();
        assertThat(query.getSqlSegment()).contains("user_id", "post_id", "AND");
        assertThat(query.getParamNameValuePairs().values()).contains(USER_ID, FIRST_POST_ID);
    }

    @Test
    @DisplayName("收藏页：按收藏时间序回填（不按帖子表返回顺序），不可见帖剔除但 total 仍按收藏行数")
    void favoritePageKeepsFavoriteOrderAndDropsInvisible() {
        stubFavoritePage(List.of(FIRST_POST_ID, SECOND_POST_ID), 2);
        // 帖子表故意倒序返回，且第二帖已下架（REMOVED）——顺序必须由收藏序决定
        when(postMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(postRow(SECOND_POST_ID, PostStatus.REMOVED), postRow(FIRST_POST_ID, PostStatus.PUBLISHED)));

        PageResult<Post> result = repository.findFavoritePosts(USER_ID, 1, 20);

        assertThat(result.items()).extracting(Post::getId).containsExactly(FIRST_POST_ID);
        assertThat(result.total()).isEqualTo(2);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(20);

        LambdaQueryWrapper<PostDO> query = capturedPostQuery();
        assertThat(query.getSqlSegment()).contains("id IN", "is_deleted");
        assertThat(query.getParamNameValuePairs().values()).contains(false, FIRST_POST_ID, SECOND_POST_ID);
    }

    @Test
    @DisplayName("收藏页：本页没有收藏行时直接返回空页、total=0，不再查帖子表")
    void emptyFavoritePageSkipsPostLookup() {
        stubFavoritePage(List.of(), 0);

        PageResult<Post> result = repository.findFavoritePosts(USER_ID, 1, 20);

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isZero();
        verify(postMapper, never()).selectList(any(Wrapper.class));
    }

    @Test
    @DisplayName("批量回读：空集合短路不发 SQL；非空按 user + post_id IN 过滤并收成不可变 Set")
    void findFavoritedPostIdsShortCircuitsAndMaps() {
        assertThat(repository.findFavoritedPostIds(USER_ID, List.of())).isEmpty();
        verify(favoriteMapper, never()).selectList(any(Wrapper.class));

        when(favoriteMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(favoriteRow(FIRST_POST_ID)));

        Set<Long> ids = repository.findFavoritedPostIds(USER_ID, List.of(FIRST_POST_ID, SECOND_POST_ID));

        assertThat(ids).containsExactly(FIRST_POST_ID);
        LambdaQueryWrapper<FavoriteDO> query = capturedFavoriteQuery();
        assertThat(query.getSqlSegment()).contains("user_id", "post_id", "IN");
        assertThat(query.getParamNameValuePairs().values()).contains(USER_ID, FIRST_POST_ID, SECOND_POST_ID);
    }

    // ---------- helpers ----------

    @SuppressWarnings("unchecked")
    private void stubFavoritePage(List<Long> postIds, long total) {
        when(favoriteMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<FavoriteDO> page = invocation.getArgument(0);
            page.setRecords(postIds.stream().map(FavoriteRepositoryImplTest::favoriteRow).toList());
            page.setTotal(total);
            return page;
        });
    }

    private static FavoriteDO favoriteRow(long postId) {
        FavoriteDO d = new FavoriteDO();
        d.setUserId(USER_ID);
        d.setPostId(postId);
        return d;
    }

    private static PostDO postRow(long id, PostStatus status) {
        PostDO d = new PostDO();
        d.setId(id);
        d.setBoardId(1L);
        d.setAuthorId(42L);
        d.setType(BoardType.QUESTION.name());
        d.setTitle("收藏的帖子");
        d.setContentMd("正文");
        d.setContentHtml("<p>正文</p>");
        d.setStatus(status.name());
        d.setIsDeleted(false);
        d.setReplyCount(0);
        d.setLikeCount(0);
        d.setViewCount(0);
        d.setFavoriteCount(1);
        d.setIsAccepted(false);
        d.setCreatedAt(CREATED_AT);
        d.setUpdatedAt(CREATED_AT);
        return d;
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<FavoriteDO> capturedDelete() {
        ArgumentCaptor<Wrapper<FavoriteDO>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(favoriteMapper).delete(captor.capture());
        return (LambdaQueryWrapper<FavoriteDO>) captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<FavoriteDO> capturedFavoriteQuery() {
        ArgumentCaptor<Wrapper<FavoriteDO>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(favoriteMapper, org.mockito.Mockito.atLeastOnce()).selectList(captor.capture());
        return (LambdaQueryWrapper<FavoriteDO>) captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<PostDO> capturedPostQuery() {
        ArgumentCaptor<Wrapper<PostDO>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(postMapper).selectList(captor.capture());
        return (LambdaQueryWrapper<PostDO>) captor.getValue();
    }
}
