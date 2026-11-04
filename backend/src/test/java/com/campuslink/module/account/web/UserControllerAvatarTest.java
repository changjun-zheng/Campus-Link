package com.campuslink.module.account.web;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.account.application.AccountApplicationService;
import com.campuslink.module.account.application.AvatarApplicationService;
import com.campuslink.module.account.application.ProfileApplicationService;
import com.campuslink.module.account.application.UserProfileApplicationService;
import com.campuslink.module.account.application.cmd.AccountCommands.SelectAvatarCommand;
import com.campuslink.module.account.domain.model.Account;
import com.campuslink.module.account.domain.model.AccountRole;
import com.campuslink.module.account.domain.model.AccountStatus;
import com.campuslink.module.account.domain.model.DefaultAvatar;
import com.campuslink.module.account.domain.model.EmailAddress;
import com.campuslink.module.account.domain.model.StudentId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.io.IOException;
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
 * 头像三端点（CR-191）的授权与出参口径：两个写端点匿名一律 4001 且不触达用例；
 * 预设列表是公开端点；已登录时 userId 只从令牌取。
 */
class UserControllerAvatarTest {

    private static final long USER_ID = 42L;
    private static final String PRESET = DefaultAvatar.paths().get(0);

    private final AvatarApplicationService avatarApplicationService = mock(AvatarApplicationService.class);
    private final UserController controller = new UserController(mock(AccountApplicationService.class),
            mock(ProfileApplicationService.class), mock(UserProfileApplicationService.class),
            avatarApplicationService);

    @Test
    @DisplayName("匿名选预设头像 → 4001，且不触达用例")
    void anonymousPresetSelectionIsUnauthorized() {
        assertThatThrownBy(() -> controller.selectAvatarPreset(new SelectAvatarCommand(PRESET), null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));

        verify(avatarApplicationService, never()).selectPreset(anyLong(), any());
    }

    @Test
    @DisplayName("匿名上传 → 4001，且不读取文件、不触达用例")
    void anonymousUploadIsUnauthorized() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[] { 1, 2, 3 });

        assertThatThrownBy(() -> controller.uploadAvatar(file, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));

        verify(avatarApplicationService, never()).upload(anyLong(), any());
    }

    @Test
    @DisplayName("已登录选预设：userId 取自 principal，回显更新后的 UserVo")
    void presetSelectionPassesPrincipalAndReturnsUserVo() {
        when(avatarApplicationService.selectPreset(USER_ID, PRESET)).thenReturn(account(PRESET));

        var response = controller.selectAvatarPreset(new SelectAvatarCommand(PRESET), user());

        assertThat(response.data().avatarUrl()).isEqualTo(PRESET);
        verify(avatarApplicationService).selectPreset(USER_ID, PRESET);
    }

    @Test
    @DisplayName("已登录上传：文件字节透传给用例，回显新 avatarUrl")
    void uploadPassesBytesAndReturnsUserVo() throws IOException {
        byte[] bytes = { 1, 2, 3 };
        String stored = "/media/avatars/42/new.jpg";
        when(avatarApplicationService.upload(USER_ID, bytes)).thenReturn(account(stored));

        var response = controller.uploadAvatar(
                new MockMultipartFile("file", "a.png", "image/png", bytes), user());

        assertThat(response.data().avatarUrl()).isEqualTo(stored);
    }

    @Test
    @DisplayName("预设列表公开：控制器直接回后端目录，不依赖登录态")
    void presetsArePublic() {
        List<String> presets = DefaultAvatar.paths();
        when(avatarApplicationService.presets()).thenReturn(presets);

        assertThat(controller.avatarPresets().data()).containsExactlyElementsOf(presets);
    }

    private static Authentication user() {
        return new UsernamePasswordAuthenticationToken(
                USER_ID, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private static Account account(String avatarUrl) {
        return Account.rehydrate(USER_ID, EmailAddress.of("dev-stu-01@dev.campuslink.local"), null,
                StudentId.of("888800001"), "模拟学生01", avatarUrl, null, null, null, null, AccountRole.USER,
                AccountStatus.ACTIVE, true, false, null, null, null);
    }
}
