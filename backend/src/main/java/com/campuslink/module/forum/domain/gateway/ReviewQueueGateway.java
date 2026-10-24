package com.campuslink.module.forum.domain.gateway;

import java.time.Instant;
import java.util.List;

/**
 * 人工巡查队列端口（F-SAFE-004 / CR-133）：按发布时间倒序的**只读**内容列表。
 *
 * <p>口径：只列未删内容（{@code is_deleted = 0}）；平台已下架的仍列出（便于恢复）；
 * 楼层所属帖子已被作者删除的一并不列（父帖不可访问，处置无意义）。
 * 本端口不提供任何写操作——下架 / 恢复复用既有 {@code ContentModerationApplicationService}。
 */
public interface ReviewQueueGateway {

    List<ReviewItem> posts(int offset, int limit);

    List<ReviewItem> replies(int offset, int limit);

    long countPosts();

    long countReplies();

    /**
     * 巡查条目。楼层 {@code title} 是正文纯文本前 60 字（不含 HTML）；
     * {@code status} 为 {@code PUBLISHED} / {@code REMOVED}。
     */
    record ReviewItem(long id, String kind, String title, String boardName,
                      String authorNickname, String status, Instant createdAt) {
    }
}
