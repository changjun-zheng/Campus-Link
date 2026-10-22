package com.campuslink.module.account.application;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.common.audit.AuditService;
import com.campuslink.config.AppProperties;
import com.campuslink.module.account.application.cmd.AccountCommands.LoginCommand;
import com.campuslink.module.account.application.cmd.AccountCommands.LoginResult;
import com.campuslink.module.account.domain.gateway.AccountRepository;
import com.campuslink.module.account.domain.gateway.RateLimitGateway;
import com.campuslink.module.account.domain.gateway.RosterGateway;
import com.campuslink.module.account.domain.gateway.SensitiveCodec;
import com.campuslink.module.account.domain.gateway.TokenIssuer;
import com.campuslink.module.account.domain.gateway.VerificationTicketStore;
import com.campuslink.module.account.domain.model.Account;
import com.campuslink.module.account.domain.model.EmailAddress;
import com.campuslink.module.account.domain.model.PhoneNumber;
import com.campuslink.module.account.domain.service.StudentVerificationService;
import com.campuslink.module.forum.application.ModerationApplicationService;
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
import static org.mockito.Mockito.when;

/**
 * 登录用例回归单测（CR-093 / F-011）：account 为邮箱**或**手机号，据形态分流查找通道；
 * 统一提示不区分"账号不存在"与"验证码错误"（防枚举），格式非法亦按登录失败同一出口。
 */
@ExtendWith(MockitoExtension.class)
class AccountApplicationServiceLoginTest {

    private static final long USER_ID = 42L;

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private SensitiveCodec codec;
    @Mock
    private CaptchaService captchaService;
    @Mock
    private TokenIssuer tokenIssuer;

    private AccountApplicationService service;

    @BeforeEach
    void setUp() {
        service = new AccountApplicationService(
                mock(StudentVerificationService.class), accountRepository, mock(RosterGateway.class),
                mock(VerificationTicketStore.class), captchaService, codec, tokenIssuer,
                mock(RateLimitGateway.class), mock(AuditService.class), new AppProperties(),
                mock(ApplicationEventPublisher.class), mock(ModerationApplicationService.class));
    }

    private Account phoneAccount() {
        return Account.rehydrate(USER_ID, null, PhoneNumber.of("13800138000"), null, "手机号用户",
                null, null, null, null, null,
                com.campuslink.module.account.domain.model.AccountRole.USER,
                com.campuslink.module.account.domain.model.AccountStatus.ACTIVE,
                true, false, null, Instant.now(), Instant.now());
    }

    private Account emailAccount() {
        return Account.rehydrate(USER_ID, EmailAddress.of("someone@example.com"), null,
                null, "邮箱用户", null, null, null, null, null,
                com.campuslink.module.account.domain.model.AccountRole.USER,
                com.campuslink.module.account.domain.model.AccountStatus.ACTIVE,
                true, false, null, Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("登录：手机号渠道走 phone_hash 查找，验证码校验与消费同键")
    void phoneChannelLoginLooksUpByPhoneHash() {
        when(codec.hash(anyString())).thenAnswer(inv -> "H(" + inv.getArgument(0) + ")");
        when(accountRepository.findByPhoneHash("H(13800138000)")).thenReturn(Optional.of(phoneAccount()));
        when(captchaService.verify("13800138000", "123456")).thenReturn(true);
        when(tokenIssuer.issue(any(), anyString())).thenReturn("jwt");
        when(tokenIssuer.ttlSeconds()).thenReturn(604800L);

        LoginResult result = service.login(new LoginCommand("13800138000", "123456"));

        assertThat(result.account().getId()).isEqualTo(USER_ID);
        org.mockito.Mockito.verify(captchaService).consume("13800138000");
    }

    @Test
    @DisplayName("登录：邮箱渠道回归保护——email_hash 查找与既有语义不变")
    void emailChannelLoginUnchanged() {
        Account account = Account.rehydrate(USER_ID, EmailAddress.of("someone@example.com"), null,
                null, "邮箱用户", null, null, null, null, null,
                com.campuslink.module.account.domain.model.AccountRole.USER,
                com.campuslink.module.account.domain.model.AccountStatus.ACTIVE,
                true, false, null, Instant.now(), Instant.now());
        when(codec.hash(anyString())).thenAnswer(inv -> "H(" + inv.getArgument(0) + ")");
        when(accountRepository.findByEmailHash("H(someone@example.com)")).thenReturn(Optional.of(account));
        when(captchaService.verify("someone@example.com", "123456")).thenReturn(true);
        when(tokenIssuer.issue(any(), anyString())).thenReturn("jwt");
        when(tokenIssuer.ttlSeconds()).thenReturn(604800L);

        LoginResult result = service.login(new LoginCommand("someone@example.com", "123456"));

        assertThat(result.account().getId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("登录：手机号渠道查无账号 -> LOGIN_FAILED（与验证码错误同一提示，防枚举）")
    void unknownPhoneAccountIsUnifiedLoginFailed() {
        when(codec.hash(anyString())).thenAnswer(inv -> "H(" + inv.getArgument(0) + ")");
        when(accountRepository.findByPhoneHash("H(13800138000)")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginCommand("13800138000", "123456")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.LOGIN_FAILED));
    }

    @Test
    @DisplayName("登录：账号格式非法（既非手机号又非邮箱形态）-> 兜底 LOGIN_FAILED")
    void malformedAccountFallsBackToLoginFailed() {
        assertThatThrownBy(() -> service.login(new LoginCommand("not-a-contact", "123456")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.LOGIN_FAILED));
    }
}
