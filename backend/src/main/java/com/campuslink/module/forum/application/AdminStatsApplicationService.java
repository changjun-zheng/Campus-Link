package com.campuslink.module.forum.application;

import com.campuslink.module.account.application.AccountApplicationService;
import com.campuslink.module.forum.domain.gateway.AdminStatsGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * 运营看板用例（F-ADMIN-005 / CR-131）：把四组只读计数组装成一次响应。
 *
 * <p>「今日」的日界在这里定唯一一处：**UTC 自然日零点**（库里 {@code created_at} 存 UTC 墙钟），
 * 传给三个内容 / 举报以外的两条内容查询。改口径只改这里，不在 SQL 里各写一份。
 *
 * <p>用户组跨上下文调 {@link AccountApplicationService#accountStats()}（ADR-012 只调对方 application），
 * 与 {@code UserProfileApplicationService} 调 forum {@code authorStats} 是同一反向方式。
 */
@Service
@RequiredArgsConstructor
public class AdminStatsApplicationService {

    private final AdminStatsGateway adminStatsGateway;
    private final AccountApplicationService accountApplicationService;

    public Dashboard dashboard() {
        Instant todayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        return new Dashboard(accountApplicationService.accountStats(),
                adminStatsGateway.postStats(todayStart),
                adminStatsGateway.replyStats(todayStart),
                adminStatsGateway.reportStats());
    }

    /** 看板出参（application 层载体；web 层再映射成扁平 VO） */
    public record Dashboard(AccountApplicationService.AccountStats users,
                            AdminStatsGateway.ContentStats posts,
                            AdminStatsGateway.ContentStats replies,
                            AdminStatsGateway.ReportStats reports) {
    }
}
