package com.campuslink.module.account.application;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.common.audit.AuditService;
import com.campuslink.config.AppProperties;
import static org.mockito.Mockito.mock;
import com.campuslink.module.account.application.cmd.AccountCommands.LoginResult;
import com.campuslink.module.account.application.cmd.AccountCommands.RegisterCommand;
import com.campuslink.module.account.domain.exception.AccountConflictException;
import com.campuslink.module.account.domain.gateway.AccountRepository;
import com.campuslink.module.account.domain.gateway.RateLimitGateway;
import com.campuslink.module.account.domain.gateway.RosterGateway;
import com.campuslink.module.account.domain.gateway.SensitiveCodec;
import com.campuslink.module.account.domain.gateway.TokenIssuer;
import com.campuslink.module.account.domain.gateway.VerificationTicketStore;
import com.campuslink.module.account.domain.model.Account;
import com.campuslink.module.account.domain.service.StudentVerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 注册用例回归单测：票据载荷是加密学号，注册必须解密后再建号。
 *
 * <p>回归背景：此前 register 直接把票据里的**密文**当学号使用，落库的 student_id 是密文而非真实学号；
 * 又因 AES-GCM 每次密文不同，"一号一账号"的 student_id_hash 唯一约束从未真正生效。
 * 该缺陷在入参校验放宽时被掩盖，加上学号 9 位格式约束后才暴露（见 CR-013）。
 */
@ExtendWith(MockitoExtension.class)
class AccountApplicationServiceRegisterTest {

    private static final String PLAIN_STUDENT_ID = "249971346";
    private static final String ENCRYPTED_STUDENT_ID = "ENC(" + PLAIN_STUDENT_ID + ")";

    @Mock
    private StudentVerificationService verificationService;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private RosterGateway rosterGateway;
    @Mock
    private VerificationTicketStore ticketStore;
    @Mock
    private CaptchaService captchaService;
    @Mock
    private SensitiveCodec codec;
    @Mock
    private TokenIssuer tokenIssuer;
    @Mock
    private RateLimitGateway rateLimitGateway;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AccountApplicationService service;

    @BeforeEach
    void setUp() {
        service = new AccountApplicationService(
                verificationService, accountRepository, rosterGateway, ticketStore, captchaService,
                codec, tokenIssuer, rateLimitGateway, mock(AuditService.class), new AppProperties(), eventPublisher);
    }

    @Test
    @DisplayName("注册：票据中的加密学号必须解密后落库，不得把密文当学号")
    void registerPersistsPlainStudentIdFromEncryptedTicket() {
        when(codec.decrypt(ENCRYPTED_STUDENT_ID)).thenReturn(PLAIN_STUDENT_ID);
        when(codec.hash(anyString())).thenAnswer(inv -> "H(" + inv.getArgument(0) + ")");
        when(ticketStore.consume("ticket-1")).thenReturn(Optional.of(ENCRYPTED_STUDENT_ID));
        when(captchaService.verify(anyString(), anyString())).thenReturn(true);
        when(accountRepository.existsByEmailHash(anyString())).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        when(rosterGateway.occupy(anyString(), any())).thenReturn(true);
        when(tokenIssuer.issue(any(), anyString())).thenReturn("jwt-token");
        when(tokenIssuer.ttlSeconds()).thenReturn(604800L);

        LoginResult result = service.register(
                new RegisterCommand("ticket-1", "someone@example.com", "123456", "张三"));

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        org.mockito.Mockito.verify(accountRepository).save(saved.capture());
        assertThat(saved.getValue().getStudentId().value()).isEqualTo(PLAIN_STUDENT_ID);

        // 占用名册用的是真实学号的哈希，而非密文的哈希
        org.mockito.Mockito.verify(rosterGateway).occupy(eq("H(" + PLAIN_STUDENT_ID + ")"), any());
        assertThat(result.account().getStudentId().value()).isEqualTo(PLAIN_STUDENT_ID);
    }

    @Test
    @DisplayName("注册：学号唯一键冲突 -> 统一为学籍核验失败提示（防名册枚举），不得返回 9999")
    void studentIdConflictMapsToUnifiedVerificationFailure() {
        stubReachingSave();
        when(accountRepository.save(any(Account.class))).thenThrow(
                new AccountConflictException(AccountConflictException.Field.STUDENT_ID, "学号已被占用", null));

        assertThatExceptionOfType(ApiException.class)
                .isThrownBy(() -> service.register(
                        new RegisterCommand("ticket-1", "someone@example.com", "123456", "张三")))
                .satisfies(e -> assertThat(e.getCode()).isEqualTo(ResultCode.STUDENT_VERIFY_FAILED));
    }

    @Test
    @DisplayName("注册：邮箱唯一键冲突 -> 明确提示邮箱已注册")
    void emailConflictMapsToEmailExists() {
        stubReachingSave();
        when(accountRepository.save(any(Account.class))).thenThrow(
                new AccountConflictException(AccountConflictException.Field.EMAIL, "邮箱已被占用", null));

        assertThatExceptionOfType(ApiException.class)
                .isThrownBy(() -> service.register(
                        new RegisterCommand("ticket-1", "someone@example.com", "123456", "张三")))
                .satisfies(e -> assertThat(e.getCode()).isEqualTo(ResultCode.EMAIL_EXISTS));
    }

    /** 桩到 save 之前所需的全部依赖（邮箱渠道冲突用例共用） */
    private void stubReachingSave() {
        when(codec.decrypt(ENCRYPTED_STUDENT_ID)).thenReturn(PLAIN_STUDENT_ID);
        when(codec.hash(anyString())).thenAnswer(inv -> "H(" + inv.getArgument(0) + ")");
        when(ticketStore.consume("ticket-1")).thenReturn(Optional.of(ENCRYPTED_STUDENT_ID));
        when(captchaService.verify(anyString(), anyString())).thenReturn(true);
        when(accountRepository.existsByEmailHash(anyString())).thenReturn(false);
    }

    // ── 手机号渠道（CR-093 / F-011）─────────────────────────────────────────

    @Test
    @DisplayName("注册：手机号渠道走 existsByPhoneHash / phone_hash 预检，聚合携带 PhoneNumber")
    void phoneRegistrationPersistsPhoneAndChecksPhoneConflict() {
        when(codec.decrypt(ENCRYPTED_STUDENT_ID)).thenReturn(PLAIN_STUDENT_ID);
        when(codec.hash(anyString())).thenAnswer(inv -> "H(" + inv.getArgument(0) + ")");
        when(ticketStore.consume("ticket-1")).thenReturn(Optional.of(ENCRYPTED_STUDENT_ID));
        when(captchaService.verify(anyString(), anyString())).thenReturn(true);
        when(accountRepository.existsByPhoneHash(anyString())).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        when(rosterGateway.occupy(anyString(), any())).thenReturn(true);
        when(tokenIssuer.issue(any(), anyString())).thenReturn("jwt-token");
        when(tokenIssuer.ttlSeconds()).thenReturn(604800L);

        LoginResult result = service.register(
                new RegisterCommand("ticket-1", "13800138000", "123456", "张三"));

        // 手机号预检走 phone_hash 通道，而非 email_hash
        org.mockito.Mockito.verify(accountRepository).existsByPhoneHash("H(13800138000)");
        assertThat(result.account().getPhone().value()).isEqualTo("13800138000");
        // 手机号渠道账号 email 侧为空（聚合允许 email 与 phone 至少其一）
        assertThat(result.account().getEmail()).isNull();
    }

    @Test
    @DisplayName("注册：手机号唯一键冲突 -> 明确提示手机号已注册（2014）")
    void phoneConflictMapsToPhoneExists() {
        stubReachingSavePhone();
        when(accountRepository.save(any(Account.class))).thenThrow(
                new AccountConflictException(AccountConflictException.Field.PHONE, "手机号已被占用", null));

        assertThatExceptionOfType(ApiException.class)
                .isThrownBy(() -> service.register(
                        new RegisterCommand("ticket-1", "13800138000", "123456", "张三")))
                .satisfies(e -> assertThat(e.getCode()).isEqualTo(ResultCode.PHONE_EXISTS));
    }

    @Test
    @DisplayName("注册：contact 同时缺省与双通道并存由 web 侧 @Pattern 拦截；预检各走各的哈希通道")
    void emailAndPhonePrechecksUseTheirOwnHashChannels() {
        when(codec.decrypt(ENCRYPTED_STUDENT_ID)).thenReturn(PLAIN_STUDENT_ID);
        when(codec.hash(anyString())).thenAnswer(inv -> "H(" + inv.getArgument(0) + ")");
        when(ticketStore.consume("ticket-1")).thenReturn(Optional.of(ENCRYPTED_STUDENT_ID));
        when(captchaService.verify(anyString(), anyString())).thenReturn(true);
        when(accountRepository.existsByEmailHash(anyString())).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        when(rosterGateway.occupy(anyString(), any())).thenReturn(true);
        when(tokenIssuer.issue(any(), anyString())).thenReturn("jwt-token");
        when(tokenIssuer.ttlSeconds()).thenReturn(604800L);

        service.register(new RegisterCommand("ticket-1", "someone@example.com", "123456", "张三"));

        org.mockito.Mockito.verify(accountRepository).existsByEmailHash("H(someone@example.com)");
        org.mockito.Mockito.verify(accountRepository, org.mockito.Mockito.never())
                .existsByPhoneHash(anyString());
    }

    /** 手机号渠道桩到 save 之前所需的全部依赖 */
    private void stubReachingSavePhone() {
        when(codec.decrypt(ENCRYPTED_STUDENT_ID)).thenReturn(PLAIN_STUDENT_ID);
        when(codec.hash(anyString())).thenAnswer(inv -> "H(" + inv.getArgument(0) + ")");
        when(ticketStore.consume("ticket-1")).thenReturn(Optional.of(ENCRYPTED_STUDENT_ID));
        when(captchaService.verify(anyString(), anyString())).thenReturn(true);
        when(accountRepository.existsByPhoneHash(anyString())).thenReturn(false);
    }
}
