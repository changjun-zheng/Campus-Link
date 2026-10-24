package com.campuslink.module.forum.application;

import com.campuslink.module.forum.domain.gateway.ReviewQueueGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 人工巡查队列用例（F-SAFE-004 / CR-133）：只读列表 + 分页归一。
 *
 * <p>{@code kind} 只认 {@code reply}，其余（空 / 非法）一律按 {@code post}——与标签列表的
 * {@code sort} 同口径，不把非法入参升级成业务错误（这是运营筛选项，不是必填参数）。
 * 处置动作不在本服务：下架 / 恢复复用既有 {@code ContentModerationApplicationService}，
 * 审计仍为 {@code MODERATE_POST} / {@code MODERATE_REPLY}。
 */
@Service
@RequiredArgsConstructor
public class ReviewQueueApplicationService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ReviewQueueGateway reviewQueueGateway;

    public ReviewQueue page(String kind, int page, int size) {
        String normalizedKind = "reply".equals(kind) ? "reply" : "post";
        int currentPage = Math.max(page, 1);
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int offset = (int) Math.min((long) (currentPage - 1) * pageSize, Integer.MAX_VALUE);

        boolean replies = "reply".equals(normalizedKind);
        long total = replies ? reviewQueueGateway.countReplies() : reviewQueueGateway.countPosts();
        List<ReviewItemView> items = (replies
                ? reviewQueueGateway.replies(offset, pageSize)
                : reviewQueueGateway.posts(offset, pageSize)).stream()
                .map(i -> new ReviewItemView(i.id(), i.kind(), i.title(), i.boardName(),
                        i.authorNickname(), i.status(), i.createdAt()))
                .toList();
        return new ReviewQueue(normalizedKind, items, total, currentPage, pageSize);
    }

    /** 分页结果（application 载体；web 层再映射成 VO） */
    public record ReviewQueue(String kind, List<ReviewItemView> items, long total, int page, int size) {
    }

    /** 巡查条目：楼层 {@code title} 为正文纯文本前 60 字 */
    public record ReviewItemView(long id, String kind, String title, String boardName,
                                 String authorNickname, String status, Instant createdAt) {
    }
}
