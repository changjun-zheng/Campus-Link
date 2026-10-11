package com.campuslink.module.account.web;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.account.application.AccountApplicationService;
import com.campuslink.module.account.application.ProfileApplicationService;
import com.campuslink.module.account.application.UserProfileApplicationService;
import com.campuslink.module.account.application.cmd.AccountCommands.UpdateProfileCommand;
import com.campuslink.module.account.domain.model.Account;
import com.campuslink.module.account.domain.model.AccountRole;
import com.campuslink.module.account.domain.model.AccountStatus;
import com.campuslink.module.account.domain.model.EmailAddress;
import com.campuslink.module.account.domain.model.StudentId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 资料编辑端点的授权与出参口径（F-ACC-007a / 007d）。
 *
 * <p>身份只从令牌取：Controller **没有**任何接收 userId 的入参，越权面靠接口形状为零（不是靠测试兜住，
 * "仅本人"这类线无机器强制，见问题清单 E-011 同类缺口）。这里守的是另一头——声明了
 * {@code @SecurityRequirement} 就必须真的调 {@code CurrentUser}（G7），摘成别的即测试变红。
 */
class UserControllerProfileTest {

    private final AccountApplicationService accountApplicationService = mock(AccountApplicationService.class);
    private final ProfileApplicationService profileApplicationService = mock(ProfileApplicationService.class);
    private final UserController controller = new UserController(accountApplicationService,
            profileApplicationService, mock(UserProfileApplicationService.class));

    @Test
    @DisplayName("匿名改资料 → 4001，且不触达编辑用例（不写库、不记审计）")
    void anonymousIsUnauthorized() {
        assertThatThrownBy(() -> controller.updateProfile(new UpdateProfileCommand("新昵称", null, null), null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));

        verify(profileApplicationService, never()).updateProfile(anyLong(), any());
    }

    @Test
    @DisplayName("已登录 → userId 取自 principal，响应是改动后的全量 UserVo（前端不必二次拉取）")
    void passesPrincipalAndReturnsFullVo() {
        when(profileApplicationService.updateProfile(anyLong(), any()))
                .thenReturn(account("新昵称", AccountStatus.ACTIVE));

        var response = controller.updateProfile(new UpdateProfileCommand("新昵称", null, null), user());

        assertThat(response.data().nickname()).isEqualTo("新昵称");
        verify(profileApplicationService).updateProfile(42L, new UpdateProfileCommand("新昵称", null, null));
    }

    @Test
    @DisplayName("GET /users/me 回显 status（设置页据此判断「正常 / 已封禁」，F-ACC-007d）")
    void meExposesStatus() {
        when(accountApplicationService.accountOf(42L)).thenReturn(account("张三同学", AccountStatus.BANNED));

        assertThat(controller.me(user()).data().status()).isEqualTo("BANNED");
    }

    @Test
    @DisplayName("契约级负向断言：UserVo 的字段名清单里没有任何敏感列或账号类型列")
    void userVoCarriesNoSensitiveColumns() {
        assertThat(Arrays.stream(UserVo.class.getRecordComponents()).map(RecordComponent::getName).toList())
                .doesNotContain("email", "emailHash", "emailEnc", "phone", "studentId", "studentIdHash",
                        "anonymized", "deleteAt", "accountType", "account_type");
    }

    private static Authentication user() {
        return new UsernamePasswordAuthenticationToken(
                42L, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private static Account account(String nickname, AccountStatus status) {
        return Account.rehydrate(42L, EmailAddress.of("dev-stu-01@dev.campuslink.local"), null, StudentId.of("888800001"), nickname, null, null, null, null, null, AccountRole.USER, status, true, false, null, null, null);
    }
}
