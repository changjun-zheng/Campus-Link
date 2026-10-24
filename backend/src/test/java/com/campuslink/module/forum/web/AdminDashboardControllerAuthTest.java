package com.campuslink.module.forum.web;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ApiResponse;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.account.application.AccountApplicationService;
import com.campuslink.module.forum.application.AdminStatsApplicationService;
import com.campuslink.module.forum.domain.gateway.AdminStatsGateway;
import com.campuslink.module.forum.web.vo.AdminDashboardVo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 数据看板端点（CR-131 / F-ADMIN-005）的授权三态：匿名 401、普通用户 403、SUPERADMIN 放行。
 *
 * <p>ROLE_USER 那一例是本端点真正的防线：路径级框架只区分「登录 / 未登录」，
 * 「仅 SUPERADMIN」只存在于 {@code CurrentUser.requireRole}——摘掉它后匿名例仍绿、本例会红。
 */
class AdminDashboardControllerAuthTest {

    private final AdminStatsApplicationService service = mock(AdminStatsApplicationService.class);
    private final AdminDashboardController controller = new AdminDashboardController(service);

    @Test
    @DisplayName("匿名看板 → 4001，且不触达用例")
    void anonymousDashboardIsUnauthorized() {
        assertThatThrownBy(() -> controller.dashboard(null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));
        verify(service, never()).dashboard();
    }

    @Test
    @DisplayName("普通用户看板 → 4002，且不触达用例")
    void ordinaryUserDashboardIsForbidden() {
        assertThatThrownBy(() -> controller.dashboard(user("ROLE_USER")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.FORBIDDEN));
        verify(service, never()).dashboard();
    }

    @Test
    @DisplayName("SUPERADMIN 看板 → 200，15 个字段按组映射")
    void superadminDashboardMapsAllFields() {
        when(service.dashboard()).thenReturn(new AdminStatsApplicationService.Dashboard(
                new AccountApplicationService.AccountStats(45, 43, 1, 1),
                new AdminStatsGateway.ContentStats(26, 25, 1, 0),
                new AdminStatsGateway.ContentStats(40, 39, 1, 2),
                new AdminStatsGateway.ReportStats(3, 1, 2)));

        ApiResponse<AdminDashboardVo> response = controller.dashboard(user("ROLE_SUPERADMIN"));

        assertThat(response.code()).isZero();
        AdminDashboardVo vo = response.data();
        assertThat(vo.userTotal()).isEqualTo(45);
        assertThat(vo.userActive()).isEqualTo(43);
        assertThat(vo.userBanned()).isEqualTo(1);
        assertThat(vo.userDeactivated()).isEqualTo(1);
        assertThat(vo.postTotal()).isEqualTo(26);
        assertThat(vo.postRemoved()).isEqualTo(1);
        assertThat(vo.replyTodayCreated()).isEqualTo(2);
        assertThat(vo.reportTotal()).isEqualTo(3);
        assertThat(vo.reportPending()).isEqualTo(1);
        assertThat(vo.reportClosed()).isEqualTo(2);
    }

    private static Authentication user(String authority) {
        return new UsernamePasswordAuthenticationToken(9L, "",
                List.of(new SimpleGrantedAuthority(authority)));
    }
}
