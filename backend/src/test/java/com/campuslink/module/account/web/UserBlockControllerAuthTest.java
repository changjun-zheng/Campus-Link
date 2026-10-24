package com.campuslink.module.account.web;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.account.application.UserBlockApplicationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 拉黑 4 个受保护端点（CR-129 / F-SOC-004）的授权三态：匿名一律 4001 且不触达用例；
 * 登录态 userId 只从令牌取，不接受入参代传（否则可代他人拉黑）。
 */
class UserBlockControllerAuthTest {

    private static final long TARGET_ID = 8L;

    private final UserBlockApplicationService service = mock(UserBlockApplicationService.class);
    private final UserBlockController controller = new UserBlockController(service);

    @Test
    @DisplayName("匿名拉黑 → 4001，且不触达用例")
    void anonymousBlockIsUnauthorized() {
        assertThatThrownBy(() -> controller.block(TARGET_ID, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));
        verify(service, never()).block(anyLong(), anyLong());
    }

    @Test
    @DisplayName("匿名解除 → 4001")
    void anonymousUnblockIsUnauthorized() {
        assertThatThrownBy(() -> controller.unblock(TARGET_ID, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));
        verify(service, never()).unblock(anyLong(), anyLong());
    }

    @Test
    @DisplayName("匿名查拉黑状态 → 4001（不泄露本人视角之外的关系）")
    void anonymousBlockStateIsUnauthorized() {
        assertThatThrownBy(() -> controller.blockState(TARGET_ID, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));
        verify(service, never()).isBlocked(anyLong(), anyLong());
    }

    @Test
    @DisplayName("匿名查黑名单 → 4001，不得读他人黑名单")
    void anonymousBlockedUsersIsUnauthorized() {
        assertThatThrownBy(() -> controller.blockedUsers(1, 20, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));
        verify(service, never()).pageBlocked(anyLong(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("登录态拉黑：userId 只从令牌取并透传")
    void blockPassesPrincipalId() {
        Authentication auth = new UsernamePasswordAuthenticationToken(1L, "", java.util.List.of());
        controller.block(TARGET_ID, auth);
        verify(service).block(1L, TARGET_ID);
    }
}
