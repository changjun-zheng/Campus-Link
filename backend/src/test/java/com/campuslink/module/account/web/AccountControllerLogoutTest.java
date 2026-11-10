package com.campuslink.module.account.web;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.account.application.AccountApplicationService;
import com.campuslink.module.account.application.CaptchaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 退出登录端点（CR-213）：匿名必须 401 / 4001（AGENTS「新增需登录的端点必须补匿名 401 用例」），
 * 登录后用户 ID 只从令牌取并透传给用例。
 */
class AccountControllerLogoutTest {

    private static final long USER_ID = 42L;

    private final AccountApplicationService accountApplicationService = mock(AccountApplicationService.class);
    private final AccountController controller = new AccountController(accountApplicationService,
            mock(CaptchaService.class));

    private Authentication user() {
        return new UsernamePasswordAuthenticationToken(USER_ID, null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    @DisplayName("匿名退出 → 4001，且不触达退出用例")
    void anonymousIsUnauthorized() {
        assertThatThrownBy(() -> controller.logout(null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));

        verifyNoInteractions(accountApplicationService);
    }

    @Test
    @DisplayName("已登录退出 → 200，用户 ID 从令牌透传")
    void authenticatedLogsOutWithTokenSubject() {
        assertThat(controller.logout(user()).code()).isZero();

        verify(accountApplicationService).logout(USER_ID);
    }
}
