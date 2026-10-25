package com.campuslink.module.forum.application;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.markdown.MarkdownRenderer;
import com.campuslink.common.result.ResultCode;
import com.campuslink.config.AppProperties;
import com.campuslink.module.account.application.AccountApplicationService;
import com.campuslink.module.account.application.FollowApplicationService;
import com.campuslink.module.forum.application.cmd.ForumResults.AuthorStats;
import com.campuslink.module.forum.application.cmd.ForumResults.PublicReplyItem;
import com.campuslink.module.forum.domain.gateway.BoardRepository;
import com.campuslink.module.forum.domain.gateway.FavoriteRepository;
import com.campuslink.module.forum.domain.gateway.LikeRepository;
import com.campuslink.module.forum.domain.gateway.PageResult;
import com.campuslink.module.forum.domain.gateway.PostRepository;
import com.campuslink.module.forum.domain.gateway.ReplyRepository;
import com.campuslink.module.forum.domain.model.PublicReplyRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 他人主页的两项新能力（CR-104）：作者计数聚合 {@code authorStats} 与公开楼层时间线
 * {@code listPublicRepliesByAuthor}。
 *
 * <p><b>本类守的是接线，SQL 里的可见性列语义不在这里守</b>——四条聚合与那条分页查询的 WHERE 是
 * {@code @Select} 字面量，Mockito 看不见；那一部分的证据落在真机取证（实施方案 §5：下架帖的楼层
 * 既不进时间线、也不进获赞与被采纳数），本类负责的是"接错一根线立刻变红"。
 *
 * <p>最值得守的两条：① {@code likeCount} 必须是<b>帖子 + 楼层两个 SUM 之和</b>（D-1 发起人定稿），
 * 漏加一侧数字小一半而页面照样渲染；② 时间线必须走 {@code findPublicPageByAuthor} 而<b>不是</b>
 * {@code findPageByAuthor}（单数那条是作者本人视角，会把被平台下架的楼层重新公开，且只差一个词）。
 */
@ExtendWith(MockitoExtension.class)
class ForumQueryApplicationServicePublicTimelineTest {

    private static final long AUTHOR_ID = 42L;
    private static final Instant CREATED_AT = Instant.parse("2026-10-15T08:00:00Z");

    @Mock
    private BoardRepository boardRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private ReplyRepository replyRepository;
    @Mock
    private LikeRepository likeRepository;
    @Mock
    private FavoriteRepository favoriteRepository;
    @Mock
    private AccountApplicationService accountApplicationService;
    @Mock
    private FollowApplicationService followApplicationService;

    @Mock
    private com.campuslink.module.account.application.UserBlockApplicationService userBlockApplicationService;

    private ForumQueryApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ForumQueryApplicationService(boardRepository, postRepository, replyRepository,
                likeRepository, favoriteRepository, accountApplicationService, userBlockApplicationService,
                followApplicationService, new MarkdownRenderer(), new AppProperties());
    }

    @Test
    @DisplayName("获赞数＝帖子 SUM ＋ 楼层 SUM（D-1）：只加一侧数字会小一半，页面照样渲染看不出来")
    void likeCountSumsPostsAndReplies() {
        when(postRepository.countVisibleByAuthor(AUTHOR_ID)).thenReturn(12L);
        when(postRepository.sumVisibleLikeCountByAuthor(AUTHOR_ID)).thenReturn(80L);
        when(replyRepository.sumVisibleLikeCountByAuthor(AUTHOR_ID)).thenReturn(25L);
        when(replyRepository.countAcceptedVisibleByAuthor(AUTHOR_ID)).thenReturn(3L);
        when(replyRepository.countVisibleByAuthor(AUTHOR_ID)).thenReturn(7L);

        AuthorStats stats = service.authorStats(AUTHOR_ID);

        assertThat(stats.postCount()).isEqualTo(12L);
        assertThat(stats.likeCount()).isEqualTo(105L);
        assertThat(stats.acceptedCount()).isEqualTo(3L);
        assertThat(stats.replyCount()).isEqualTo(7L);
    }

    @Test
    @DisplayName("四个数取自同一次调用：分页与两个 SUM 各只问一次，资料卡上的数字不许来自四个时刻")
    void authorStatsIsASingleRoundTrip() {
        when(postRepository.countVisibleByAuthor(AUTHOR_ID)).thenReturn(0L);
        when(postRepository.sumVisibleLikeCountByAuthor(AUTHOR_ID)).thenReturn(0L);
        when(replyRepository.sumVisibleLikeCountByAuthor(AUTHOR_ID)).thenReturn(0L);
        when(replyRepository.countAcceptedVisibleByAuthor(AUTHOR_ID)).thenReturn(0L);
        when(replyRepository.countVisibleByAuthor(AUTHOR_ID)).thenReturn(0L);

        service.authorStats(AUTHOR_ID);

        verify(postRepository).countVisibleByAuthor(AUTHOR_ID);
        verify(postRepository).sumVisibleLikeCountByAuthor(AUTHOR_ID);
        verify(replyRepository).sumVisibleLikeCountByAuthor(AUTHOR_ID);
        verify(replyRepository).countAcceptedVisibleByAuthor(AUTHOR_ID);
        verify(replyRepository).countVisibleByAuthor(AUTHOR_ID);
    }

    @Test
    @DisplayName("无内容时四个计数如实回 0（不回落 null、也不省略字段——前端「获赞 0」与「取不到」是两种状态）")
    void emptyAuthorReportsZeros() {
        when(postRepository.countVisibleByAuthor(AUTHOR_ID)).thenReturn(0L);
        when(postRepository.sumVisibleLikeCountByAuthor(AUTHOR_ID)).thenReturn(0L);
        when(replyRepository.sumVisibleLikeCountByAuthor(AUTHOR_ID)).thenReturn(0L);
        when(replyRepository.countAcceptedVisibleByAuthor(AUTHOR_ID)).thenReturn(0L);
        when(replyRepository.countVisibleByAuthor(AUTHOR_ID)).thenReturn(0L);

        AuthorStats stats = service.authorStats(AUTHOR_ID);

        assertThat(stats.postCount()).isZero();
        assertThat(stats.likeCount()).isZero();
        assertThat(stats.acceptedCount()).isZero();
        assertThat(stats.replyCount()).isZero();
    }

    @Test
    @DisplayName("authorStats 不重复校验账号：唯一调用方（account 的资料聚合）已先过同一道 2007 门")
    void authorStatsDoesNotRecheckTheAccount() {
        when(postRepository.countVisibleByAuthor(AUTHOR_ID)).thenReturn(1L);
        when(postRepository.sumVisibleLikeCountByAuthor(AUTHOR_ID)).thenReturn(2L);
        when(replyRepository.sumVisibleLikeCountByAuthor(AUTHOR_ID)).thenReturn(4L);
        when(replyRepository.countAcceptedVisibleByAuthor(AUTHOR_ID)).thenReturn(1L);
        when(replyRepository.countVisibleByAuthor(AUTHOR_ID)).thenReturn(1L);

        service.authorStats(AUTHOR_ID);

        verify(accountApplicationService, never()).publicAccountOf(anyLong());
    }

    @Test
    @DisplayName("公开楼层时间线：作者不可公开 → 2007 / 404，且一条楼层都不查（不给探针当放大器）")
    void invisibleAuthorShortCircuitsReplyTimeline() {
        when(accountApplicationService.publicAccountOf(AUTHOR_ID))
                .thenThrow(new ApiException(ResultCode.USER_NOT_FOUND));

        assertThatThrownBy(() -> service.listPublicRepliesByAuthor(AUTHOR_ID, 1, 20))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.USER_NOT_FOUND));

        verify(replyRepository, never()).findPublicPageByAuthor(anyLong(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("反证守门：走 findPublicPageByAuthor（路人），绝不走 findPageByAuthor（本人视角会露出被下架楼层）")
    void replyTimelineDeliberatelyUsesThePublicPath() {
        when(accountApplicationService.publicAccountOf(AUTHOR_ID)).thenReturn(null);
        when(replyRepository.findPublicPageByAuthor(AUTHOR_ID, 1, 20))
                .thenReturn(new PageResult<>(List.of(), 0, 1, 20));

        service.listPublicRepliesByAuthor(AUTHOR_ID, 1, 20);

        verify(replyRepository, never()).findPageByAuthor(anyLong(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("条目定位三件套齐全（postId + postTitle + floorNo）且摘要是服务端截断的纯文本，不是原文")
    void itemsCarryParentLocationAndTruncatedSummary() {
        String longText = "这是一段很长的回答".repeat(30);
        when(accountApplicationService.publicAccountOf(AUTHOR_ID)).thenReturn(null);
        when(replyRepository.findPublicPageByAuthor(AUTHOR_ID, 1, 20)).thenReturn(new PageResult<>(
                List.of(new PublicReplyRow(9L, 7L, "Spring 事务失效的场景", 3, longText, CREATED_AT)), 1, 1, 20));

        PageResult<PublicReplyItem> page = service.listPublicRepliesByAuthor(AUTHOR_ID, 1, 20);

        PublicReplyItem item = page.items().getFirst();
        assertThat(item.id()).isEqualTo(9L);
        assertThat(item.postId()).isEqualTo(7L);
        assertThat(item.postTitle()).isEqualTo("Spring 事务失效的场景");
        assertThat(item.floorNo()).isEqualTo(3);
        assertThat(item.summary()).hasSizeLessThanOrEqualTo(120);
        assertThat(item.summary()).doesNotContain(longText);
        assertThat(item.createdAt()).isEqualTo(CREATED_AT);
    }

    @Test
    @DisplayName("分页归一与列表回显：page<1 → 1、size>100 → 100（与全站列表、发帖时间线同一口径）")
    void replyTimelinePagingIsNormalized() {
        when(accountApplicationService.publicAccountOf(AUTHOR_ID)).thenReturn(null);
        when(replyRepository.findPublicPageByAuthor(AUTHOR_ID, 1, 100))
                .thenReturn(new PageResult<>(List.of(), 0, 1, 100));

        PageResult<PublicReplyItem> page = service.listPublicRepliesByAuthor(AUTHOR_ID, 0, 500);

        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(100);
    }

    @Test
    @DisplayName("total 与 SQL 条件同源：条目被过滤时计数跟着变，不出现「楼层 20 条却只列出 3 条」")
    void totalComesFromTheSameQueryAsItems() {
        when(accountApplicationService.publicAccountOf(AUTHOR_ID)).thenReturn(null);
        when(replyRepository.findPublicPageByAuthor(AUTHOR_ID, 2, 5)).thenReturn(new PageResult<>(
                List.of(new PublicReplyRow(1L, 7L, "T", 8, "短答案", CREATED_AT)), 8, 2, 5));

        PageResult<PublicReplyItem> page = service.listPublicRepliesByAuthor(AUTHOR_ID, 2, 5);

        assertThat(page.total()).isEqualTo(8L);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().summary()).isEqualTo("短答案");
    }
}
