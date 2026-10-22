package com.campuslink.module.account.web;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.account.application.AccountApplicationService;
import com.campuslink.module.account.application.CaptchaService;
import com.campuslink.module.account.application.cmd.AccountCommands.LoginResult;
import com.campuslink.module.account.domain.model.Account;
import com.campuslink.module.account.domain.model.AccountRole;
import com.campuslink.module.account.domain.model.AccountStatus;
import com.campuslink.module.account.domain.model.EmailAddress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 续签端点（CR-116）：匿名必须 401 / 4001（AGENTS「新增需登录的端点必须补匿名 401 用例」），
 * 登录后 ID 只从令牌取并透传给用例。
 */
class AccountControllerRefreshTest {

        private static final long USER_ID = 42L;

        private final AccountApplicationService accountApplicationService = mock(AccountApplicationService.class);
        private final AccountController controller = new AccountController(accountApplicationService,
                        mock(CaptchaService.class));

        private Authentication user() {
                return new UsernamePasswordAuthenticationToken(USER_ID, null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        }

        @Test
        @DisplayName("匿名 → 4001，且不触达续签用例")
        void anonymousIsUnauthorized() {
                assertThatThrownBy(() -> controller.refresh(null))
                                .isInstanceOfSatisfying(ApiException.class,
                                                e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));

                verifyNoInteractions(accountApplicationService);
        }

        @Test
        @DisplayName("已登录 → 用令牌里的用户 ID 续签，响应复用 AuthResponse 形状")
        void authenticatedRefreshesWithTokenSubject() {
                Account account = Account.rehydrate(USER_ID, EmailAddress.of("someone@example.com"), null,
                                null, "续签用户", null, null, null, null, null,
                                AccountRole.USER, AccountStatus.ACTIVE, true, false, null, Instant.now(),
                                Instant.now());
                when(accountApplicationService.refresh(USER_ID))
                                .thenReturn(new LoginResult("jwt-refreshed",
                                                Instant.now().plusSeconds(604800).getEpochSecond(),
                                                account));

                var response = controller.refresh(user());

                assertThat(response.data().token()).isEqualTo("jwt-refreshed");
                assertThat(response.data().user().nickname()).isEqualTo("续签用户");
                verify(accountApplicationService).refresh(USER_ID);
        }

        @Test
        @DisplayName("续签失败（封禁 / 注销中）由用例抛错，控制器不吞异常")
        void failureFromUseCasePropagates() {
                when(accountApplicationService.refresh(USER_ID))
                                .thenThrow(new ApiException(ResultCode.ACCOUNT_DEACTIVATED));

                assertThatThrownBy(() -> controller.refresh(user()))
                                .isInstanceOfSatisfying(ApiException.class,
                                                e -> assertThat(e.getCode()).isEqualTo(ResultCode.ACCOUNT_DEACTIVATED));
        }
}
