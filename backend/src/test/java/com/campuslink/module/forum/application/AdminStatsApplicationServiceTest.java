package com.campuslink.module.forum.application;

import com.campuslink.module.account.application.AccountApplicationService;
import com.campuslink.module.forum.domain.gateway.AdminStatsGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 运营看板用例（F-ADMIN-005 / CR-131）。
 *
 * <p>守两条口径：① 四组数字原样组装、不换算不猜补；②「今日」日界是 **UTC 自然日零点**，
 * 且只在这一处产生（两条内容查询拿到同一个起点）。
 *
 * <p>SQL 侧的排除自删 / 单列下架 / COALESCE 由 {@code AdminStatsMapperSqlTest} 读注解文本守。
 */
@ExtendWith(MockitoExtension.class)
class AdminStatsApplicationServiceTest {

    @Mock
    private AdminStatsGateway adminStatsGateway;
    @Mock
    private AccountApplicationService accountApplicationService;
    @InjectMocks
    private AdminStatsApplicationService service;

    @Test
    @DisplayName("看板：四组计数原样组装，用户 / 帖子 / 楼层 / 举报互不串位")
    void dashboardAssemblesGroups() {
        when(accountApplicationService.accountStats())
                .thenReturn(new AccountApplicationService.AccountStats(45, 43, 1, 1));
        when(adminStatsGateway.postStats(any())).thenReturn(new AdminStatsGateway.ContentStats(26, 25, 1, 0));
        when(adminStatsGateway.replyStats(any())).thenReturn(new AdminStatsGateway.ContentStats(40, 39, 1, 2));
        when(adminStatsGateway.reportStats()).thenReturn(new AdminStatsGateway.ReportStats(3, 1, 2));

        AdminStatsApplicationService.Dashboard d = service.dashboard();

        assertThat(d.users().total()).isEqualTo(45);
        assertThat(d.users().active()).isEqualTo(43);
        assertThat(d.users().banned()).isEqualTo(1);
        assertThat(d.users().deactivated()).isEqualTo(1);
        assertThat(d.posts().total()).isEqualTo(26);
        assertThat(d.posts().todayCreated()).isZero();
        assertThat(d.replies().todayCreated()).isEqualTo(2);
        assertThat(d.reports().pending()).isEqualTo(1);
        assertThat(d.reports().closed()).isEqualTo(2);
    }

    @Test
    @DisplayName("今日日界：UTC 自然日零点，两条内容查询拿到同一 Instant")
    void todayStartIsUtcMidnightAndShared() {
        when(adminStatsGateway.postStats(any())).thenReturn(new AdminStatsGateway.ContentStats(0, 0, 0, 0));
        when(adminStatsGateway.replyStats(any())).thenReturn(new AdminStatsGateway.ContentStats(0, 0, 0, 0));
        when(adminStatsGateway.reportStats()).thenReturn(new AdminStatsGateway.ReportStats(0, 0, 0));

        service.dashboard();

        ArgumentCaptor<Instant> postStart = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> replyStart = ArgumentCaptor.forClass(Instant.class);
        verify(adminStatsGateway).postStats(postStart.capture());
        verify(adminStatsGateway).replyStats(replyStart.capture());
        Instant expected = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        assertThat(postStart.getValue()).isEqualTo(expected);
        assertThat(replyStart.getValue()).isEqualTo(expected);
    }
}
