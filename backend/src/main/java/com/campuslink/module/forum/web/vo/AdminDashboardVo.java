package com.campuslink.module.forum.web.vo;

import com.campuslink.module.forum.application.AdminStatsApplicationService;

/**
 * 运营看板视图（F-ADMIN-005 / CR-131）：四组计数**扁平**为 15 个字段，不嵌套。
 *
 * <p>为什么扁平：嵌套 record 会让 springdoc 多生成三个顶层 schema（简单名有撞名风险），
 * 而本 VO 只服务于一张页面；字段前缀（{@code user*} / {@code post*} / {@code reply*} / {@code report*}）
 * 已足够表达分组，前端按前缀归列即可。
 *
 * <p>口径（与实施方案 §7 一致）：内容组 {@code total} 只含未删内容；{@code removed} 是平台下架、
 * 计入 total；{@code todayCreated} 按 **UTC 自然日**；用户组 {@code total} 含已注销、并单列
 * {@code userDeactivated}。
 */
public record AdminDashboardVo(
        long userTotal, long userActive, long userBanned, long userDeactivated,
        long postTotal, long postPublished, long postRemoved, long postTodayCreated,
        long replyTotal, long replyPublished, long replyRemoved, long replyTodayCreated,
        long reportTotal, long reportPending, long reportClosed) {

    public static AdminDashboardVo from(AdminStatsApplicationService.Dashboard d) {
        return new AdminDashboardVo(
                d.users().total(), d.users().active(), d.users().banned(), d.users().deactivated(),
                d.posts().total(), d.posts().published(), d.posts().removed(), d.posts().todayCreated(),
                d.replies().total(), d.replies().published(), d.replies().removed(), d.replies().todayCreated(),
                d.reports().total(), d.reports().pending(), d.reports().closed());
    }
}
