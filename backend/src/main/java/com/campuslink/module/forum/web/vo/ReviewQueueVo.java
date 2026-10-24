package com.campuslink.module.forum.web.vo;

import com.campuslink.module.forum.application.ReviewQueueApplicationService;

import java.time.Instant;
import java.util.List;

/**
 * 人工巡查队列视图（F-SAFE-004 / CR-133）：分页形状沿用 {@code items / total / page / size}，
 * 另带 {@code kind} 回显（服务端归一后的值，前端据此保持类型切换与数据一致）。
 *
 * <p>楼层 {@code title} 是正文纯文本前 60 字（服务端 {@code LEFT(content_md, 60)} 截取），
 * **不含 HTML**；前端以插值渲染，不做二次解析。
 */
public record ReviewQueueVo(String kind, List<ReviewItemVo> items, long total, int page, int size) {

    public record ReviewItemVo(long id, String kind, String title, String boardName,
                               String authorNickname, String status, Instant createdAt) {
    }

    public static ReviewQueueVo from(ReviewQueueApplicationService.ReviewQueue result) {
        return new ReviewQueueVo(result.kind(),
                result.items().stream()
                        .map(i -> new ReviewItemVo(i.id(), i.kind(), i.title(), i.boardName(),
                                i.authorNickname(), i.status(), i.createdAt()))
                        .toList(),
                result.total(), result.page(), result.size());
    }
}
