package com.campuslink.module.account.application;

import com.campuslink.common.audit.AuditService;
import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.config.AppProperties;
import com.campuslink.module.account.application.cmd.AccountCommands.LoginResult;
import com.campuslink.module.account.domain.gateway.AccountRepository;
import com.campuslink.module.account.domain.gateway.RateLimitGateway;
import com.campuslink.module.account.domain.gateway.RosterGateway;
import com.campuslink.module.account.domain.gateway.SensitiveCodec;
import com.campuslink.module.account.domain.gateway.TokenIssuer;
import com.campuslink.module.account.domain.gateway.VerificationTicketStore;
import com.campuslink.module.account.domain.model.Account;
import com.campuslink.module.account.domain.model.AccountRole;
import com.campuslink.module.account.domain.model.AccountStatus;
import com.campuslink.module.account.domain.model.EmailAddress;
import com.campuslink.module.account.domain.service.StudentVerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 登录态续签用例回归（CR-116 / F-ACC-001）：活跃账号换新 token；封禁 / 注销中 / 不存在一律拒绝。
 *
 * <p>续签必须重读账号而不是直接按 claims 重签——否则封禁与注销冷静期的账号都能靠续签
 * 把会话无限延长（这正是不续签 DEACTIVATED 的理由）。
 */
@ExtendWith(MockitoExtension.class)
class AccountApplicationServiceRefreshTest {

    private static final long USER_ID = 42L;
    private static final long TTL_SECONDS = 7L * 24 * 3600;

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private TokenIssuer tokenIssuer;

    private AccountApplicationService service;

    @BeforeEach
    void setUp() {
        service = new AccountApplicationService(
                mock(StudentVerificationService.class), accountRepository, mock(RosterGateway.class),
                mock(VerificationTicketStore.class), mock(CaptchaService.class), mock(SensitiveCodec.class),
                tokenIssuer, mock(RateLimitGateway.class), mock(AuditService.class), new AppProperties(),
                mock(ApplicationEventPublisher.class));
    }

    private Account account(AccountStatus status) {
        return Account.rehydrate(USER_ID, EmailAddress.of("someone@example.com"), null,
                null, "续签用户", null, null, null, null, null,
                AccountRole.USER, status, true, false, null, Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("活跃账号：签发新 token，过期时间落在 ttl 之后")
    void activeAccountGetsNewToken() {
        when(accountRepository.findById(USER_ID)).thenReturn(Optional.of(account(AccountStatus.ACTIVE)));
        when(tokenIssuer.issue(USER_ID, AccountRole.USER.name())).thenReturn("jwt-refreshed");
        when(tokenIssuer.ttlSeconds()).thenReturn(TTL_SECONDS);

        long before = Instant.now().getEpochSecond();
        LoginResult result = service.refresh(USER_ID);

        assertThat(result.token()).isEqualTo("jwt-refreshed");
        assertThat(result.account().getId()).isEqualTo(USER_ID);
        assertThat(result.expiresAtEpochSecond()).isGreaterThanOrEqualTo(before + TTL_SECONDS);
    }

    @Test
    @DisplayName("封禁账号拒绝续签：USER_BANNED，且不签发 token")
    void bannedAccountIsRejected() {
        when(accountRepository.findById(USER_ID)).thenReturn(Optional.of(account(AccountStatus.BANNED)));

        assertThatThrownBy(() -> service.refresh(USER_ID))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.USER_BANNED));
        verifyNoInteractions(tokenIssuer);
    }

    @Test
    @DisplayName("注销冷静期拒绝续签：ACCOUNT_DEACTIVATED（须重新登录以撤销注销）")
    void deactivatedAccountIsRejected() {
        when(accountRepository.findById(USER_ID)).thenReturn(Optional.of(account(AccountStatus.DEACTIVATED)));

        assertThatThrownBy(() -> service.refresh(USER_ID))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.ACCOUNT_DEACTIVATED));
        verifyNoInteractions(tokenIssuer);
    }

    @Test
    @DisplayName("账号不存在：USER_NOT_FOUND，且不签发 token")
    void missingAccountIsRejected() {
        when(accountRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refresh(USER_ID))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.USER_NOT_FOUND));
        verifyNoInteractions(tokenIssuer);
    }
}
