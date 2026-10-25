package com.campuslink.module.forum.web.vo;

import com.campuslink.module.forum.application.cmd.ForumResults.MyPostSummary;

import java.time.Instant;

/**
 * "我的帖子"条目视图（F-ACC-007b）：与全站列表共享的 {@link PostSummaryVo} **不同形**——
 * 多一个只面向作者的 {@code status}，少掉互动计数与作者昵称（"我的"页里它们是噪音）。
 *
 * <p>不给的东西同样是口径的一部分：**不下发正文原文、不下发下架原因**（原因在
 * {@code audit_logs.detail}，属运营侧）。{@code boardCode} 只用于"发在哪个版块"的回显；
 * {@code status=REMOVED} 的条目由前端渲染"已下架"标记且**不可点**（点进详情是 404 / 3001）。
 */
public record MyPostSummaryVo(Long id, String boardCode, String title, String summary,
                             String status, Instant createdAt, boolean anonymous) {

    public static MyPostSummaryVo from(MyPostSummary item) {
        return new MyPostSummaryVo(item.id(), item.boardCode(), item.title(), item.summary(),
                item.status(), item.createdAt(), item.anonymous());
    }
}
