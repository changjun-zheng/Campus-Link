package com.campuslink.module.account.web;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.account.application.RosterImportApplicationService;
import com.campuslink.module.account.application.cmd.RosterImportResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 名册导入的授权三态：未登录 401、角色不足 403、SUPERADMIN 放行。
 * 前两者必须分开——合并成 403 会让前端无法区分“去登录”和“没权限”（技术方案 §5）。
 */
class AdminRosterControllerAuthTest {

    private final RosterImportApplicationService rosterImportService = mock(RosterImportApplicationService.class);
    private final AdminRosterController controller = new AdminRosterController(rosterImportService);

    @Test
    @DisplayName("未登录 → 4001，且不触达导入用例")
    void anonymousIsUnauthorized() {
        assertThatThrownBy(() -> controller.importRoster("学号,姓名\n249990001,测试甲\n", "b1", null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));

        verify(rosterImportService, never()).importCsv(any(), anyString(), any());
    }

    @Test
    @DisplayName("已登录但角色不足 → 4002，与未登录区分开")
    void ordinaryUserIsForbidden() {
        assertThatThrownBy(() -> controller.importRoster("学号,姓名\n249990001,测试甲\n", "b1", user("ROLE_USER")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.FORBIDDEN));

        verify(rosterImportService, never()).importCsv(any(), anyString(), any());
    }

    @Test
    @DisplayName("OPS 导入名册 → 4002（名册管理仍仅 SUPERADMIN）")
    void opsIsForbidden() {
        assertThatThrownBy(() -> controller.importRoster("学号,姓名\n249990001,测试甲\n", "b1", user("ROLE_OPS")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.FORBIDDEN));

        verify(rosterImportService, never()).importCsv(any(), anyString(), any());
    }

    @Test
    @DisplayName("SUPERADMIN 放行，操作人 ID 取自 principal 并透传给审计")
    void superadminIsAllowedAndOperatorIdPropagated() {
        when(rosterImportService.importCsv(any(), anyString(), any()))
                .thenReturn(new RosterImportResult(1, 0, 0));

        var response = controller.importRoster("学号,姓名\n249990001,测试甲\n", "b1", user("ROLE_SUPERADMIN"));

        assertThat(response.data().inserted()).isEqualTo(1);
        verify(rosterImportService).importCsv(
                eq(List.of("学号,姓名", "249990001,测试甲")),
                eq("b1"),
                eq(42L));
    }

    private static Authentication user(String role) {
        return new UsernamePasswordAuthenticationToken(
                42L, null, List.of(new SimpleGrantedAuthority(role)));
    }
}
