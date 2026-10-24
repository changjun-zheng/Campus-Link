package com.campuslink.module.forum.application;

import com.campuslink.module.forum.domain.gateway.ReviewQueueGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * 人工巡查队列用例（F-SAFE-004 / CR-133）。
 *
 * <p>守三条：① {@code kind} 只认 {@code reply}，其余归一到 {@code post}；② 分页归一
 * （page &lt; 1 → 1、size 越界 → 1 / 100）与 offset 计算；③ 两类内容各走各的查询与计数，
 * 不串数据（帖子查询不会被楼层分支调用）。
 */
@ExtendWith(MockitoExtension.class)
class ReviewQueueApplicationServiceTest {

    @Mock
    private ReviewQueueGateway reviewQueueGateway;
    @InjectMocks
    private ReviewQueueApplicationService service;

    @Test
    @DisplayName("kind=reply：走楼层查询与楼层计数")
    void replyKindUsesReplyQueries() {
        when(reviewQueueGateway.countReplies()).thenReturn(2L);
        when(reviewQueueGateway.replies(0, 20)).thenReturn(List.of(
                new ReviewQueueGateway.ReviewItem(31, "reply", "楼层摘要", "技术问答",
                        "张三", "PUBLISHED", Instant.parse("2026-10-24T10:00:00Z"))));

        ReviewQueueApplicationService.ReviewQueue result = service.page("reply", 1, 20);

        assertThat(result.kind()).isEqualTo("reply");
        assertThat(result.total()).isEqualTo(2);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).boardName()).isEqualTo("技术问答");
        verify(reviewQueueGateway).replies(0, 20);
        verify(reviewQueueGateway).countReplies();
        verifyNoMoreInteractions(reviewQueueGateway);
    }

    @Test
    @DisplayName("kind 非法 / 空：归一到 post，并回显归一后的 kind")
    void unknownKindFallsBackToPosts() {
        when(reviewQueueGateway.countPosts()).thenReturn(0L);
        when(reviewQueueGateway.posts(0, 20)).thenReturn(List.of());

        ReviewQueueApplicationService.ReviewQueue result = service.page("weird", 1, 20);

        assertThat(result.kind()).isEqualTo("post");
        assertThat(result.items()).isEmpty();
        verify(reviewQueueGateway).posts(0, 20);
        verify(reviewQueueGateway).countPosts();
        verifyNoMoreInteractions(reviewQueueGateway);
    }

    @Test
    @DisplayName("分页归一：page 0 → 1、size 999 → 100；page 3 / size 10 → offset 20")
    void paginationIsNormalized() {
        when(reviewQueueGateway.countPosts()).thenReturn(0L);
        when(reviewQueueGateway.posts(0, 100)).thenReturn(List.of());
        when(reviewQueueGateway.posts(20, 10)).thenReturn(List.of());

        ReviewQueueApplicationService.ReviewQueue first = service.page(null, 0, 999);
        ReviewQueueApplicationService.ReviewQueue third = service.page("post", 3, 10);

        assertThat(first.page()).isEqualTo(1);
        assertThat(first.size()).isEqualTo(100);
        assertThat(third.page()).isEqualTo(3);
        assertThat(third.size()).isEqualTo(10);
    }
}
