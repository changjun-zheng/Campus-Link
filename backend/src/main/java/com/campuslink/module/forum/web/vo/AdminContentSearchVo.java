package com.campuslink.module.forum.web.vo;

import com.campuslink.module.forum.application.AdminContentSearchApplicationService;

import java.time.Instant;
import java.util.List;

/**
 * 后台内容搜索结果视图（F-ADMIN-002 / CR-141）：分页形状沿用 {@code items / total / page / size}，
 * 并回显服务端归一后的 {@code kind}。
 *
 * <p>{@code summary} 是正文纯文本前 200 字（服务端截取），**不含 HTML / Markdown 原文**；
 * 前端以插值渲染，不做二次解析。
 */
public record AdminContentSearchVo(String kind, List<ItemVo> items, long total, int page, int size) {

    public record ItemVo(long id, String kind, String title, String summary, String boardName,
                         long authorId, String authorNickname, String status, Instant createdAt) {
    }

    public static AdminContentSearchVo from(AdminContentSearchApplicationService.AdminContentPage result) {
        return new AdminContentSearchVo(result.kind(),
                result.items().stream()
                        .map(i -> new ItemVo(i.id(), i.kind(), i.title(), i.summary(), i.boardName(),
                                i.authorId(), i.authorNickname(), i.status(), i.createdAt()))
                        .toList(),
                result.total(), result.page(), result.size());
    }
}
