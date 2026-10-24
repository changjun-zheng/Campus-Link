package com.campuslink.module.forum.domain.gateway;

import java.time.Instant;

/**
 * 运营看板统计端口（F-ADMIN-005 / CR-131）：三组**只读聚合**，每组一条 SQL，
 * 不逐行取数据、不引入缓存表。
 *
 * <p>口径（与实施方案一致）：内容组的 {@code total} 只含未删内容（{@code is_deleted = 0}），
 * 平台已下架的计入 {@code total} 并单列 {@code removed}，作者自删一律不计；
 * {@code todayCreated} 以调用方传入的日界（UTC 自然日零点）为起点。
 */
public interface AdminStatsGateway {

    /** 帖子组 */
    ContentStats postStats(Instant todayStart);

    /** 楼层组：与帖子组同形状、同口径 */
    ContentStats replyStats(Instant todayStart);

    /** 举报组：全部工单（含待处理与已闭环） */
    ReportStats reportStats();

    /** 内容组（帖子 / 楼层共用）：total 只含未删内容 */
    record ContentStats(long total, long published, long removed, long todayCreated) {
    }

    /** 举报组：total = pending + closed */
    record ReportStats(long total, long pending, long closed) {
    }
}
