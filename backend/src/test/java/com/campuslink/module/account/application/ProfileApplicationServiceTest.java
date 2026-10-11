package com.campuslink.module.account.application;

import com.campuslink.common.audit.AuditService;
import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.account.application.cmd.AccountCommands.UpdateProfileCommand;
import com.campuslink.module.account.domain.gateway.AccountRepository;
import com.campuslink.module.account.domain.gateway.RateLimitGateway;
import com.campuslink.module.account.domain.model.Account;
import com.campuslink.module.account.domain.model.AccountRole;
import com.campuslink.module.account.domain.model.AccountStatus;
import com.campuslink.module.account.domain.model.EmailAddress;
import com.campuslink.module.account.domain.model.ProfileField;
import com.campuslink.module.account.domain.model.StudentId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

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
 * 资料编辑用例编排（F-ACC-007a + 007e）：限流 → 状态门 → 聚合改规则 → 定向 UPDATE → 审计。
 *
 * <p>本类的存在理由是 PRD 那两条"无机审期间唯一的缓解措施"——限流与审计。二者都是**顺序敏感**的规则：
 * 限流必须在读库之前（第 11 次不该产生任何副作用），审计必须与 UPDATE 同生同灭且幂等时不记。
 */
@ExtendWith(MockitoExtension.class)
class ProfileApplicationServiceTest {

    private static final long USER_ID = 42L;

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private RateLimitGateway rateLimitGateway;
    @Mock
    private AuditService auditService;

    private ProfileApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ProfileApplicationService(accountRepository, rateLimitGateway, auditService);
    }

    @Test
    @DisplayName("改昵称：定向 UPDATE 只带变化列，且同事务记 1 行 PROFILE_UPDATE（detail 只有字段名）")
    void recordsAuditWithFieldNamesOnly() {
        when(rateLimitGateway.hitAndCount(anyString(), any())).thenReturn(1L);
        Account account = account(AccountStatus.ACTIVE, "模拟学生01", null, null);
        when(accountRepository.findById(USER_ID)).thenReturn(Optional.of(account));

        var result = service.updateProfile(USER_ID, new UpdateProfileCommand("新昵称", null, null));

        assertThat(result.getNickname()).isEqualTo("新昵称");
        verify(accountRepository).updateProfile(eq(USER_ID), eq(account), eq(Set.of(ProfileField.NICKNAME)));
        verify(auditService).record(USER_ID, "PROFILE_UPDATE", "users", USER_ID, "fields=nickname");
    }

    @Test
    @DisplayName("detail 只记**变更字段名**：不落新值也不落旧值（新值已在 users 行内，落两份是敏感信息放大）")
    void auditDetailCarriesNoUserInput() {
        when(rateLimitGateway.hitAndCount(anyString(), any())).thenReturn(3L);
        when(accountRepository.findById(USER_ID))
                .thenReturn(Optional.of(account(AccountStatus.ACTIVE, "张三同学", "软件工程", "旧签名")));

        service.updateProfile(USER_ID, new UpdateProfileCommand(null, "计算机科学与技术", "新签名值"));

        verify(auditService).record(eq(USER_ID), eq("PROFILE_UPDATE"), eq("users"), eq(USER_ID),
                org.mockito.ArgumentMatchers.argThat(detail -> {
                    assertThat(detail).isEqualTo("fields=major,bio");
                    assertThat(detail).doesNotContain("新签名值", "计算机科学与技术", "旧签名", "dev-stu-01", "888800001");
                    return true;
                }));
    }

    @Test
    @DisplayName("幂等：三值全等 → 不发 UPDATE、不记审计，但**配额已消耗**（空提交绕不过限流）")
    void identicalSubmissionIsSideEffectFreeButCounts() {
        when(rateLimitGateway.hitAndCount("profile:edit:" + USER_ID, Duration.ofHours(1))).thenReturn(5L);
        when(accountRepository.findById(USER_ID))
                .thenReturn(Optional.of(account(AccountStatus.ACTIVE, "张三同学", "软件工程", "想走 Java")));

        var result = service.updateProfile(USER_ID, new UpdateProfileCommand("张三同学", "软件工程", "想走 Java"));

        assertThat(result.getNickname()).isEqualTo("张三同学");
        verify(accountRepository, never()).updateProfile(anyLong(), any(), any());
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("限流判据是先增后判：第 10 次放行、第 11 次拒（写成 >= 会把配额悄悄砍成 9 次）")
    void tenthHitPassesAndEleventhRejects() {
        when(rateLimitGateway.hitAndCount(anyString(), any())).thenReturn(10L);
        when(accountRepository.findById(USER_ID)).thenReturn(Optional.of(account(AccountStatus.ACTIVE, "张三同学", null, null)));

        service.updateProfile(USER_ID, new UpdateProfileCommand("新昵称", null, null));

        verify(accountRepository).updateProfile(eq(USER_ID), any(), eq(Set.of(ProfileField.NICKNAME)));

        when(rateLimitGateway.hitAndCount(anyString(), any())).thenReturn(11L);
        assertThatThrownBy(() -> service.updateProfile(USER_ID, new UpdateProfileCommand("再改一次", null, null)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.PROFILE_UPDATE_TOO_FREQUENT));
    }

    @ParameterizedTest
    @ValueSource(longs = {12L, 99L})
    @DisplayName("超限那一趟**不触达任何副作用**：不读库、不写库、不记审计（PRD 硬断言）")
    void overLimitDoesNotReachTheAggregate(long hits) {
        when(rateLimitGateway.hitAndCount(anyString(), any())).thenReturn(hits);

        assertThatThrownBy(() -> service.updateProfile(USER_ID, new UpdateProfileCommand("新昵称", null, null)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.PROFILE_UPDATE_TOO_FREQUENT));

        verifyNoInteractions(accountRepository);
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("封禁账号拒绝：403 / 2006 且零副作用（不复用 4002，与「没权限」区分开）")
    void bannedAccountIsRejectedWithoutSideEffects() {
        when(rateLimitGateway.hitAndCount(anyString(), any())).thenReturn(1L);
        when(accountRepository.findById(USER_ID))
                .thenReturn(Optional.of(account(AccountStatus.BANNED, "张三同学", null, null)));

        assertThatThrownBy(() -> service.updateProfile(USER_ID, new UpdateProfileCommand("新昵称", null, null)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.USER_BANNED));

        verify(accountRepository, never()).updateProfile(anyLong(), any(), any());
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("注销冷静期（DEACTIVATED）按 PRD 允许编辑——⚠️ 本期它**无路径可达**（登录判定用 isActive()）")
    void deactivatedAccountMayStillEdit() {
        when(rateLimitGateway.hitAndCount(anyString(), any())).thenReturn(1L);
        when(accountRepository.findById(USER_ID))
                .thenReturn(Optional.of(account(AccountStatus.DEACTIVATED, "张三同学", null, null)));

        service.updateProfile(USER_ID, new UpdateProfileCommand("新昵称", null, null));

        verify(accountRepository).updateProfile(eq(USER_ID), any(), eq(Set.of(ProfileField.NICKNAME)));
    }

    @Test
    @DisplayName("坏值（bio 含尖括号）→ 1001，且不落库、不记审计（校验先于写入）")
    void invalidValueRejectsBeforeWrite() {
        when(rateLimitGateway.hitAndCount(anyString(), any())).thenReturn(1L);
        when(accountRepository.findById(USER_ID)).thenReturn(Optional.of(account(AccountStatus.ACTIVE, "张三同学", null, null)));

        assertThatThrownBy(() -> service.updateProfile(USER_ID, new UpdateProfileCommand(null, null, "<script>alert(1)</script>")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));

        verify(accountRepository, never()).updateProfile(anyLong(), any(), any());
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("账号查不到 → 404 / 2007（令牌有效但行已被清，不静默成功）")
    void missingAccountYieldsNotFound() {
        when(rateLimitGateway.hitAndCount(anyString(), any())).thenReturn(1L);
        when(accountRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateProfile(USER_ID, new UpdateProfileCommand("新昵称", null, null)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.USER_NOT_FOUND));

        verifyNoInteractions(auditService);
    }

    private static Account account(AccountStatus status, String nickname, String major, String bio) {
        return Account.rehydrate(USER_ID, EmailAddress.of("dev-stu-01@dev.campuslink.local"), null, StudentId.of("888800001"), nickname, null, null, major, null, bio, AccountRole.USER, status, true, false, null, null, null);
    }
}
