package com.campuslink.module.forum.infrastructure.persistence;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campuslink.module.forum.domain.gateway.PageResult;
import com.campuslink.module.forum.domain.model.BoardType;
import com.campuslink.module.forum.domain.model.HotScoreInput;
import com.campuslink.module.forum.domain.model.Post;
import com.campuslink.module.forum.domain.model.PostSortOrder;
import com.campuslink.module.forum.domain.model.PostStatus;
import com.campuslink.module.forum.domain.model.SimilarPostRow;
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

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code PostRepositoryImpl} 的**非可见性方法**覆盖（CR-111 覆盖率批，补 E-002 的 module 缺口）。
 *
 * <p>为什么与既有的四支专测分开：那四支（{@code CountVisibleByAuthor} / {@code FindPageByAuthor} /
 * {@code MarkDeleted} / {@code UpdateStatus}）守的是**口径**（可见性、护栏、幂等），各只盯一个方法；
 * 本类补的是**其余读路径与计数写路径**（列表 / 搜索 / 按 id / 保存 / 浏览数 / 回复数 / 采纳 / 赞数与收藏数 /
 * 热榜候选与重置 / 相似帖），目标是让这些方法至少被执行一次、并把「WHERE 里到底有没有那个条件」钉住。
 *
 * <p><b>本类不假装覆盖语义</b>：把 UPDATE 的 `setSql` 断言成字符串，只能证明"写的是这条语句"，
 * 不能证明数据库真的按它执行——后者归真机取证与集成测试（{@code SpringBootContextTest}）。覆盖率是下限，不是证明。
 */
@ExtendWith(MockitoExtension.class)
class PostRepositoryImplTest {

    private static final long POST_ID = 7L;
    private static final long BOARD_ID = 1L;
    private static final long AUTHOR_ID = 42L;
    private static final Instant CREATED_AT = Instant.parse("2026-10-15T08:00:00Z");

    @Mock
    private PostMapper postMapper;

    private PostRepositoryImpl repository;

    @BeforeAll
    static void initTableInfoCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(PostMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, PostDO.class);
    }

    @BeforeEach
    void setUp() {
        repository = new PostRepositoryImpl(postMapper);
    }

    @Test
    @DisplayName("列表（最新）：可见性两条齐全、无版块时不写 board_id、按 created_at + id 倒序、不按热度")
    @SuppressWarnings("unchecked")
    void listsLatestWithoutBoardFilter() {
        when(postMapper.selectPage(any(IPage.class), any(Wrapper.class)))
                .thenReturn(pageOf(List.of(doRow()), 1));

        PageResult<Post> result = repository.findPage(null, PostSortOrder.LATEST, 1, 20);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().getId()).isEqualTo(POST_ID);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(20);

        String sql = capturedPageQuery().getSqlSegment();
        assertThat(sql).contains("status", "is_deleted", "ORDER BY");
        assertThat(sql).doesNotContain("board_id");
        assertThat(sql).doesNotContain("hot_score");
    }

    @Test
    @DisplayName("列表（热门）：多一个 hot_score 排序；带版块时 board_id 进 WHERE")
    @SuppressWarnings("unchecked")
    void listsHotWithinBoard() {
        when(postMapper.selectPage(any(IPage.class), any(Wrapper.class)))
                .thenReturn(pageOf(List.of(), 0));

        repository.findPage(BOARD_ID, PostSortOrder.HOT, 2, 10);

        LambdaQueryWrapper<PostDO> query = capturedPageQuery();
        String sql = query.getSqlSegment();
        assertThat(sql).contains("hot_score", "board_id");
        assertThat(query.getParamNameValuePairs().values()).contains(BOARD_ID);
    }

    @Test
    @DisplayName("搜索：关键词 / 版块 / 天数三参原样透传给 Mapper，出参转领域并按页回显")
    void searchPassesParamsThrough() {
        when(postMapper.search(any(IPage.class), eq("事务"), eq(BOARD_ID), eq(30)))
                .thenReturn(pageOf(List.of(doRow()), 1));

        PageResult<Post> result = repository.search("事务", BOARD_ID, 30, 1, 20);

        assertThat(result.items()).hasSize(1);
        assertThat(result.total()).isEqualTo(1);
        verify(postMapper).search(any(IPage.class), eq("事务"), eq(BOARD_ID), eq(30));
    }

    @Test
    @DisplayName("按 id 读：只认未删除行（墓碑视为不存在），查不到回 Optional.empty")
    @SuppressWarnings("unchecked")
    void findByIdSkipsTombstones() {
        when(postMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        assertThat(repository.findById(POST_ID)).isEmpty();
        assertThat(capturedSelectOneQuery().getSqlSegment()).contains("is_deleted");
        assertThat(capturedSelectOneQuery().getParamNameValuePairs()).containsValue(false);

        when(postMapper.selectOne(any(Wrapper.class))).thenReturn(doRow());
        assertThat(repository.findById(POST_ID)).map(Post::getId).hasValue(POST_ID);
    }

    @Test
    @DisplayName("批量按 id：空 / null 集合不发 SQL（避免 IN () 非法语句），非空带 is_deleted 过滤")
    @SuppressWarnings("unchecked")
    void findByIdsShortCircuitsOnEmpty() {
        assertThat(repository.findByIds(List.of())).isEmpty();
        assertThat(repository.findByIds(null)).isEmpty();
        verify(postMapper, never()).selectList(any(Wrapper.class));

        when(postMapper.selectList(any(Wrapper.class))).thenReturn(List.of(doRow()));
        assertThat(repository.findByIds(List.of(POST_ID))).hasSize(1);

        ArgumentCaptor<Wrapper<PostDO>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(postMapper).selectList(captor.capture());
        assertThat(((LambdaQueryWrapper<PostDO>) captor.getValue()).getSqlSegment()).contains("id", "is_deleted");
    }

    @Test
    @DisplayName("保存：领域对象转 DO 后 insert，再用回填后的 DO 转回领域（id 由库回填）")
    void saveRoundTripsThroughDo() {
        Post post = post();
        when(postMapper.insert(any(PostDO.class))).thenAnswer(inv -> {
            PostDO d = inv.getArgument(0);
            d.setId(POST_ID);
            return 1;
        });

        Post saved = repository.save(post);

        ArgumentCaptor<PostDO> captor = ArgumentCaptor.forClass(PostDO.class);
        verify(postMapper).insert(captor.capture());
        PostDO inserted = captor.getValue();
        assertThat(inserted.getTitle()).isEqualTo("事务为什么失效");
        assertThat(inserted.getStatus()).isEqualTo(PostStatus.PUBLISHED.name());
        assertThat(inserted.getBoardId()).isEqualTo(BOARD_ID);
        assertThat(saved.getId()).isEqualTo(POST_ID);
    }

    @Test
    @DisplayName("浏览数：定向 setSql 自增（不整行回写），WHERE 只有 id")
    @SuppressWarnings("unchecked")
    void incrementViewCountUsesTargetedSql() {
        repository.incrementViewCount(POST_ID);

        LambdaUpdateWrapper<PostDO> wrapper = capturedUpdateWrapper();
        assertThat(wrapper.getSqlSet()).contains("view_count = view_count + 1");
        assertThat(wrapper.getSqlSegment()).contains("id");
    }

    @Test
    @DisplayName("回复数：先自增再读回，返回值即本次楼层号来源（设计 §4.1 的行锁串行化）")
    void incrementReplyCountReadsBack() {
        PostDO after = doRow();
        after.setReplyCount(6);
        when(postMapper.selectById(POST_ID)).thenReturn(after);

        assertThat(repository.incrementReplyCountAndGet(POST_ID)).isEqualTo(6);

        LambdaUpdateWrapper<PostDO> wrapper = capturedUpdateWrapper();
        assertThat(wrapper.getSqlSet()).contains("reply_count = reply_count + 1");
    }

    @Test
    @DisplayName("采纳：两列同写（is_accepted + accepted_reply_id），后写覆盖前写即「可更换」语义")
    @SuppressWarnings("unchecked")
    void updateAcceptedReplySetsBothColumns() {
        repository.updateAcceptedReply(POST_ID, 99L);

        LambdaUpdateWrapper<PostDO> wrapper = capturedUpdateWrapper();
        assertThat(wrapper.getSqlSet()).contains("is_accepted", "accepted_reply_id");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(99L, true);
    }

    @Test
    @DisplayName("赞数与收藏数：按 delta 拼进 setSql 后读回新值（两列不能写串）")
    void adjustsLikeAndFavoriteCounts() {
        PostDO after = doRow();
        after.setLikeCount(4);
        after.setFavoriteCount(2);
        when(postMapper.selectById(POST_ID)).thenReturn(after);

        assertThat(repository.adjustLikeCount(POST_ID, 1)).isEqualTo(4);
        assertThat(capturedUpdateWrapper().getSqlSet()).contains("like_count = like_count + 1");

        assertThat(repository.adjustFavoriteCount(POST_ID, -1)).isEqualTo(2);
        assertThat(capturedUpdateWrapper().getSqlSet()).contains("favorite_count = favorite_count + -1");
    }

    @Test
    @DisplayName("热榜候选：只取 5 列、窗口起点与游标条件齐全、searchCount=false（只要这一批不要总数）")
    @SuppressWarnings("unchecked")
    void findHotCandidatesUsesCursorAndNoCount() {
        when(postMapper.selectPage(any(IPage.class), any(Wrapper.class)))
                .thenReturn(pageOf(List.of(doRow()), 0));

        List<HotScoreInput> inputs = repository.findHotCandidates(CREATED_AT, 200, 100L);

        assertThat(inputs).hasSize(1);
        assertThat(inputs.getFirst().id()).isEqualTo(POST_ID);

        ArgumentCaptor<IPage<PostDO>> pageCaptor = ArgumentCaptor.forClass(IPage.class);
        verify(postMapper).selectPage(pageCaptor.capture(), any(Wrapper.class));
        assertThat(pageCaptor.getValue().searchCount()).isFalse();
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(200);

        LambdaQueryWrapper<PostDO> query = capturedPageQuery();
        assertThat(query.getSqlSegment()).contains("status", "is_deleted", "created_at", "id");
        assertThat(query.getParamNameValuePairs().values()).contains(100L);
    }

    @Test
    @DisplayName("重置热榜老分：只碰分数非 0 且「已删 / 非 PUBLISHED / 早于窗口」三选一的行")
    @SuppressWarnings("unchecked")
    void resetHotScoresOnlyTouchesNonCandidatesWithNonZeroScore() {
        when(postMapper.update(any(), any(Wrapper.class))).thenReturn(3);

        assertThat(repository.resetHotScoresBefore(CREATED_AT)).isEqualTo(3);

        LambdaUpdateWrapper<PostDO> wrapper = capturedUpdateWrapper();
        assertThat(wrapper.getSqlSet()).contains("hot_score");
        String sql = wrapper.getSqlSegment();
        assertThat(sql).contains("hot_score", "is_deleted", "status", "created_at", "OR");
    }

    @Test
    @DisplayName("相似帖：replyCount 为 null 回落 0、isAccepted 为 null 视为 false（快照里可空的两列）")
    void findSimilarNormalizesNullableColumns() {
        PostDO row = doRow();
        row.setReplyCount(null);
        row.setIsAccepted(null);
        when(postMapper.findSimilar("事务", BOARD_ID, AUTHOR_ID, 5)).thenReturn(List.of(row));

        List<SimilarPostRow> rows = repository.findSimilar("事务", BOARD_ID, AUTHOR_ID, 5);

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().replyCount()).isZero();
        assertThat(rows.getFirst().accepted()).isFalse();
        verify(postMapper).findSimilar("事务", BOARD_ID, AUTHOR_ID, 5);
    }

    // ---------- helpers ----------

    private static IPage<PostDO> pageOf(List<PostDO> rows, long total) {
        Page<PostDO> page = new Page<>(1, 20);
        page.setRecords(rows);
        page.setTotal(total);
        return page;
    }

    private static PostDO doRow() {
        PostDO d = new PostDO();
        d.setId(POST_ID);
        d.setBoardId(BOARD_ID);
        d.setAuthorId(AUTHOR_ID);
        d.setType(BoardType.QUESTION.name());
        d.setTitle("事务为什么失效");
        d.setContentMd("正文");
        d.setContentHtml("<p>正文</p>");
        d.setStatus(PostStatus.PUBLISHED.name());
        d.setIsDeleted(false);
        d.setReplyCount(0);
        d.setLikeCount(0);
        d.setViewCount(0);
        d.setFavoriteCount(0);
        d.setIsAccepted(false);
        d.setCreatedAt(CREATED_AT);
        d.setUpdatedAt(CREATED_AT);
        return d;
    }

    private static Post post() {
        return Post.rehydrate(null, BOARD_ID, AUTHOR_ID, BoardType.QUESTION, "事务为什么失效",
                "正文", "<p>正文</p>", PostStatus.PUBLISHED, 0, 0, 0, null, List.of(), false, null,
                CREATED_AT, CREATED_AT);
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<PostDO> capturedPageQuery() {
        ArgumentCaptor<Wrapper<PostDO>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(postMapper, org.mockito.Mockito.atLeastOnce()).selectPage(any(IPage.class), captor.capture());
        return (LambdaQueryWrapper<PostDO>) captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<PostDO> capturedSelectOneQuery() {
        ArgumentCaptor<Wrapper<PostDO>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(postMapper, org.mockito.Mockito.atLeastOnce()).selectOne(captor.capture());
        return (LambdaQueryWrapper<PostDO>) captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private LambdaUpdateWrapper<PostDO> capturedUpdateWrapper() {
        ArgumentCaptor<Wrapper<PostDO>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(postMapper, org.mockito.Mockito.atLeastOnce()).update(any(), captor.capture());
        return (LambdaUpdateWrapper<PostDO>) captor.getValue();
    }
}
