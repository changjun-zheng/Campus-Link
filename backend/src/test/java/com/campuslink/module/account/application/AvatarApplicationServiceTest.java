package com.campuslink.module.account.application;

import com.campuslink.common.audit.AuditService;
import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.account.domain.gateway.AccountRepository;
import com.campuslink.module.account.domain.gateway.AvatarImageGateway;
import com.campuslink.module.account.domain.gateway.RateLimitGateway;
import com.campuslink.module.account.domain.model.Account;
import com.campuslink.module.account.domain.model.AccountRole;
import com.campuslink.module.account.domain.model.AccountStatus;
import com.campuslink.module.account.domain.model.DefaultAvatar;
import com.campuslink.module.account.domain.model.EmailAddress;
import com.campuslink.module.account.domain.model.StudentId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 头像用例（CR-191 / F-ACC-007h）：限流 → 白名单 / 状态门 → 定向 UPDATE → 审计，以及
 * 「事务提交后才删旧自定义文件」这条顺序敏感规则。
 */
@ExtendWith(MockitoExtension.class)
class AvatarApplicationServiceTest {

    private static final long USER_ID = 42L;
    private static final String PRESET = DefaultAvatar.paths().get(0);
    private static final String OLD_CUSTOM = "/media/avatars/42/old.jpg";
    private static final String NEW_CUSTOM = "/media/avatars/42/new.jpg";

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private AvatarImageGateway avatarImageGateway;
    @Mock
    private RateLimitGateway rateLimitGateway;
    @Mock
    private AuditService auditService;

    private AvatarApplicationService service;

    @BeforeEach
    void setUp() {
        service = new AvatarApplicationService(accountRepository, avatarImageGateway, rateLimitGateway,
                auditService);
    }

    @Test
    @DisplayName("预设目录来自后端 DefaultAvatar：8 款且与注册分配同源")
    void presetsComeFromBackendCatalog() {
        assertThat(service.presets()).containsExactlyElementsOf(DefaultAvatar.paths()).hasSize(8);
    }

    @Test
    @DisplayName("白名单外的路径 → 400 / 2016，且不读库、不写库、不记审计")
    void presetOutsideWhitelistIsRejectedBeforeReadingAccount() {
        when(rateLimitGateway.hitAndCount(anyString(), any())).thenReturn(1L);

        assertThatThrownBy(() -> service.selectPreset(USER_ID, "https://evil.example/avatar.png"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.AVATAR_INVALID));

        verifyNoInteractions(accountRepository);
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("限流第 11 次 → 429 / 2017，且零副作用（读库与审计都不该发生）")
    void overLimitDoesNotReachTheAggregate() {
        when(rateLimitGateway.hitAndCount("avatar:update:" + USER_ID, Duration.ofHours(1))).thenReturn(11L);

        assertThatThrownBy(() -> service.selectPreset(USER_ID, PRESET))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.AVATAR_UPDATE_TOO_FREQUENT));

        verifyNoInteractions(accountRepository);
        verifyNoInteractions(auditService);
        verifyNoInteractions(avatarImageGateway);
    }

    @Test
    @DisplayName("封禁账号 → 403 / 2006，且不写 UPDATE、不记审计")
    void bannedAccountIsRejectedWithoutSideEffects() {
        when(rateLimitGateway.hitAndCount(anyString(), any())).thenReturn(1L);
        when(accountRepository.findById(USER_ID))
                .thenReturn(Optional.of(account(AccountStatus.BANNED, null)));

        assertThatThrownBy(() -> service.selectPreset(USER_ID, PRESET))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.USER_BANNED));

        verify(accountRepository, never()).updateAvatar(anyLong(), anyString());
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("选用预置头像：定向 UPDATE + 1 行 AVATAR_UPDATE（detail 只记 source=preset）")
    void selectPresetUpdatesAvatarWithAudit() {
        when(rateLimitGateway.hitAndCount(anyString(), any())).thenReturn(1L);
        when(accountRepository.findById(USER_ID))
                .thenReturn(Optional.of(account(AccountStatus.ACTIVE, "/avatars/avatar-fox-02-wink.png")));

        Account updated = service.selectPreset(USER_ID, PRESET);

        assertThat(updated.getAvatarUrl()).isEqualTo(PRESET);
        verify(accountRepository).updateAvatar(USER_ID, PRESET);
        verify(auditService).record(USER_ID, "AVATAR_UPDATE", "users", USER_ID, "source=preset");
    }

    @Test
    @DisplayName("幂等：已经是该预置头像 → 不发 UPDATE、不记审计（但配额已消耗）")
    void identicalPresetIsSideEffectFree() {
        when(rateLimitGateway.hitAndCount(anyString(), any())).thenReturn(5L);
        when(accountRepository.findById(USER_ID))
                .thenReturn(Optional.of(account(AccountStatus.ACTIVE, PRESET)));

        Account updated = service.selectPreset(USER_ID, PRESET);

        assertThat(updated.getAvatarUrl()).isEqualTo(PRESET);
        verify(accountRepository, never()).updateAvatar(anyLong(), anyString());
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("上传：写库 + 审计（source=upload），并在无事务时立即删除旧自定义文件")
    void uploadStoresAndDeletesOldCustomAvatar() {
        when(rateLimitGateway.hitAndCount(anyString(), any())).thenReturn(1L);
        when(accountRepository.findById(USER_ID))
                .thenReturn(Optional.of(account(AccountStatus.ACTIVE, OLD_CUSTOM)));
        when(avatarImageGateway.store(eq(USER_ID), any())).thenReturn(NEW_CUSTOM);

        Account updated = service.upload(USER_ID, new byte[] { 1, 2, 3 });

        assertThat(updated.getAvatarUrl()).isEqualTo(NEW_CUSTOM);
        verify(accountRepository).updateAvatar(USER_ID, NEW_CUSTOM);
        verify(auditService).record(USER_ID, "AVATAR_UPDATE", "users", USER_ID, "source=upload");
        verify(avatarImageGateway).delete(OLD_CUSTOM);
    }

    @Test
    @DisplayName("首次上传（旧值为 null）不触发删除")
    void uploadWithoutOldAvatarSkipsDelete() {
        when(rateLimitGateway.hitAndCount(anyString(), any())).thenReturn(1L);
        when(accountRepository.findById(USER_ID))
                .thenReturn(Optional.of(account(AccountStatus.ACTIVE, null)));
        when(avatarImageGateway.store(eq(USER_ID), any())).thenReturn(NEW_CUSTOM);

        service.upload(USER_ID, new byte[] { 1, 2, 3 });

        verify(avatarImageGateway, never()).delete(anyString());
    }

    @Test
    @DisplayName("适配器判定图片不合法 → 2016 原样上抛，且不写库、不记审计")
    void invalidImagePropagatesWithoutWrite() {
        when(rateLimitGateway.hitAndCount(anyString(), any())).thenReturn(1L);
        when(accountRepository.findById(USER_ID))
                .thenReturn(Optional.of(account(AccountStatus.ACTIVE, OLD_CUSTOM)));
        when(avatarImageGateway.store(eq(USER_ID), any()))
                .thenThrow(new ApiException(ResultCode.AVATAR_INVALID));

        assertThatThrownBy(() -> service.upload(USER_ID, new byte[] { 1 }))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.AVATAR_INVALID));

        verify(accountRepository, never()).updateAvatar(anyLong(), anyString());
        verifyNoInteractions(auditService);
    }

    private static Account account(AccountStatus status, String avatarUrl) {
        return Account.rehydrate(USER_ID, EmailAddress.of("dev-stu-01@dev.campuslink.local"), null,
                StudentId.of("888800001"), "模拟学生01", avatarUrl, null, null, null, null, AccountRole.USER,
                status, true, false, null, null, null);
    }
}
