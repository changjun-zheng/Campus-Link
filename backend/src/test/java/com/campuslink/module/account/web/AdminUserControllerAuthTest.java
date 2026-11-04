package com.campuslink.module.account.web;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.account.application.AccountApplicationService;
import com.campuslink.module.account.domain.model.AccountStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 用户治理端点（CR-082 / CR-196）：封禁 / 解封与角色授予均仅 SUPERADMIN。
 *
 * <p>OPS 是「运营」，不是「管理员」：可处理工单与审核内容，但不得直接封禁用户或授予角色。
 * 这两例是角色层级（SUPERADMIN 叠加 ROLE_OPS）之外的真正边界——OPS 只有 ROLE_OPS。
 */
class AdminUserControllerAuthTest {

    private final AccountApplicationService service = mock(AccountApplicationService.class);
    private final AdminUserController controller = new AdminUserController(service);

    @Test
    @DisplayName("匿名封禁 → 4001，且不触达用例")
    void anonymousStatusIsUnauthorized() {
        assertThatThrownBy(() -> controller.setStatus(9L, new AdminUserController.StatusCommand("BANNED"), null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));
        verify(service, never()).adminSetStatus(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("OPS 封禁 → 4002（封禁仍仅 SUPERADMIN）")
    void opsStatusIsForbidden() {
        assertThatThrownBy(() -> controller.setStatus(9L, new AdminUserController.StatusCommand("BANNED"), auth("ROLE_OPS")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.FORBIDDEN));
        verify(service, never()).adminSetStatus(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("SUPERADMIN 封禁 → 200，操作人与目标透传")
    void superadminStatusPassesThrough() {
        assertThat(controller.setStatus(9L, new AdminUserController.StatusCommand("BANNED"), superadmin()).code()).isZero();
        verify(service).adminSetStatus(7L, 9L, AccountStatus.BANNED);
    }

    @Test
    @DisplayName("匿名授予角色 → 4001，且不触达用例")
    void anonymousRoleIsUnauthorized() {
        assertThatThrownBy(() -> controller.promoteToOps(9L, new AdminUserController.RoleCommand("OPS"), null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));
        verify(service, never()).adminPromoteToOps(anyLong(), anyLong());
    }

    @Test
    @DisplayName("OPS / 普通用户授予角色 → 4002，且不触达用例")
    void nonSuperadminRoleIsForbidden() {
        assertThatThrownBy(() -> controller.promoteToOps(9L, new AdminUserController.RoleCommand("OPS"), auth("ROLE_OPS")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.FORBIDDEN));
        assertThatThrownBy(() -> controller.promoteToOps(9L, new AdminUserController.RoleCommand("OPS"), auth("ROLE_USER")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.FORBIDDEN));
        verify(service, never()).adminPromoteToOps(anyLong(), anyLong());
    }

    @Test
    @DisplayName("SUPERADMIN 授予 OPS → 200，操作人与目标透传")
    void superadminPromotesToOps() {
        assertThat(controller.promoteToOps(9L, new AdminUserController.RoleCommand("OPS"), superadmin()).code()).isZero();
        verify(service).adminPromoteToOps(7L, 9L);
    }

    @Test
    @DisplayName("非法角色值 → 1001，且不触达用例")
    void invalidRoleIsRejected() {
        assertThatThrownBy(() -> controller.promoteToOps(9L, new AdminUserController.RoleCommand("SUPERADMIN"), superadmin()))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));
        verify(service, never()).adminPromoteToOps(anyLong(), anyLong());
    }

    private static Authentication auth(String authority) {
        return new UsernamePasswordAuthenticationToken(8L, "",
                List.of(new SimpleGrantedAuthority(authority)));
    }

    private static Authentication superadmin() {
        return new UsernamePasswordAuthenticationToken(7L, "",
                List.of(new SimpleGrantedAuthority("ROLE_SUPERADMIN"),
                        new SimpleGrantedAuthority("ROLE_OPS")));
    }
}
