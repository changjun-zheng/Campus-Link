package com.campuslink.module.account.application;

import com.campuslink.common.audit.AuditService;
import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.config.AppProperties;
import com.campuslink.module.account.application.cmd.AccountCommands.LoginCommand;
import com.campuslink.module.account.application.cmd.AccountCommands.LoginResult;
import com.campuslink.module.account.application.cmd.AccountCommands.RegisterCommand;
import com.campuslink.module.account.application.cmd.AccountCommands.VerifyStudentCommand;
import com.campuslink.module.account.application.cmd.AccountCommands.VerifyStudentResult;
import com.campuslink.module.account.domain.event.AccountRegisteredEvent;
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
import com.campuslink.module.account.domain.model.StudentId;
import com.campuslink.module.account.domain.service.StudentVerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 账号用例中**既有四支专测没覆盖**的分支（CR-111 覆盖率批）：
 * 学籍核验（限流 / 统一失败提示 / 票据载荷加密）、注册的前置失败链（验证码 / 票据 / 双方渠道冲突 / 名册占用）、
 * 登录的账号状态机（冷静期撤销 / 期满拒登 / 封禁）、注销冷静期写入、发帖前置门与管理员处置。
 *
 * <p>分工：{@code ...LoginTest} 守渠道分流与反枚举，{@code ...RegisterTest} 守票据解密与唯一键冲突翻译，
 * {@code ...NicknameTest} / {@code ...PublicAccountTest} 守读侧两个门。本类不重复这些断言，只补缺口。
 *
 * <p>本类不证明并发语义：{@code rosterGateway.occupy} 返回 false 时的"整个事务回滚"由
 * {@code @Transactional} 承担，单测只能证明**抛了正确的码**。
 */
@ExtendWith(MockitoExtension.class)
class AccountApplicationServiceTest {

    private static final long USER_ID = 42L;
    private static final String STUDENT_ID = "249971346";
    private static final String ENCRYPTED_STUDENT_ID = "ENC(" + STUDENT_ID + ")";
    private static final String EMAIL = "someone@example.com";
    private static final String PHONE = "13800138000";

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
    private AuditService auditService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AppProperties props;
    private AccountApplicationService service;

    @BeforeEach
    void setUp() {
        props = new AppProperties();
        service = new AccountApplicationService(verificationService, accountRepository, rosterGateway, ticketStore,
                captchaService, codec, tokenIssuer, rateLimitGateway, auditService, props, eventPublisher);
    }

    // ── 学籍核验（F-ACC-004）────────────────────────────────────────────

    @Test
    @DisplayName("核验：IP 小时计数超过上限 → 429 / 2103，连名册都不查")
    void verifyStudentRateLimitedByIp() {
        when(rateLimitGateway.hitAndCount("verify:ip:1.2.3.4", Duration.ofHours(1))).thenReturn(11L);

        assertThatThrownBy(() -> service.verifyStudent(
                new VerifyStudentCommand(STUDENT_ID, "张三"), "1.2.3.4"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.VERIFY_RATE_LIMITED));

        verifyNoInteractions(verificationService, ticketStore);
    }

    @Test
    @DisplayName("核验：名册比对不通过 → 统一 2101，不签发票据（三种失败对外不可区分）")
    void verifyStudentFailureIsUnified() {
        when(rateLimitGateway.hitAndCount(anyString(), any())).thenReturn(1L);
        when(verificationService.matches(any(StudentId.class), anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.verifyStudent(
                new VerifyStudentCommand(STUDENT_ID, "张三"), "1.2.3.4"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.STUDENT_VERIFY_FAILED));

        verify(ticketStore, never()).issue(anyString(), any());
    }

    @Test
    @DisplayName("核验通过：票据载荷是**加密后**的学号（不得含明文学号），TTL 取配置值")
    void verifyStudentIssuesTicketWithEncryptedPayload() {
        when(rateLimitGateway.hitAndCount(anyString(), any())).thenReturn(1L);
        when(verificationService.matches(any(StudentId.class), eq("张三"))).thenReturn(true);
        when(codec.encrypt(STUDENT_ID)).thenReturn(ENCRYPTED_STUDENT_ID);
        when(ticketStore.issue(ENCRYPTED_STUDENT_ID, Duration.ofMinutes(5))).thenReturn("ticket-1");

        VerifyStudentResult result = service.verifyStudent(
                new VerifyStudentCommand(STUDENT_ID, "张三"), "1.2.3.4");

        assertThat(result.ticket()).isEqualTo("ticket-1");
        verify(ticketStore).issue(ENCRYPTED_STUDENT_ID, Duration.ofMinutes(5));
    }

    // ── 注册前置失败链 ───────────────────────────────────────────────────

    @Test
    @DisplayName("注册：验证码不对 → 2001，票据不被消费（用户可重试，票据不浪费）")
    void registerStopsOnInvalidCaptcha() {
        when(captchaService.verify(anyString(), eq("000000"))).thenReturn(false);

        assertThatThrownBy(() -> service.register(
                new RegisterCommand("ticket-1", EMAIL, "000000", "张三")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.CAPTCHA_INVALID));

        verify(ticketStore, never()).consume(anyString());
        verifyNoInteractions(accountRepository, rosterGateway, eventPublisher);
    }

    @Test
    @DisplayName("注册：票据不存在 / 已过期 → 2102，不建号")
    void registerStopsOnInvalidTicket() {
        when(captchaService.verify(anyString(), anyString())).thenReturn(true);
        when(ticketStore.consume("ticket-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(
                new RegisterCommand("ticket-1", EMAIL, "123456", "张三")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.VERIFY_TICKET_INVALID));

        verifyNoInteractions(accountRepository, rosterGateway, eventPublisher);
    }

    @Test
    @DisplayName("注册：邮箱已存在 → 2004（预检命中，不走到建号）")
    void registerStopsWhenEmailExists() {
        stubThroughTicket();
        when(accountRepository.existsByEmailHash(anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.register(
                new RegisterCommand("ticket-1", EMAIL, "123456", "张三")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.EMAIL_EXISTS));

        verify(accountRepository, never()).save(any());
        verifyNoInteractions(rosterGateway, eventPublisher);
    }

    @Test
    @DisplayName("注册：手机号已存在 → 2014（手机号渠道预检走 phone_hash）")
    void registerStopsWhenPhoneExists() {
        stubThroughTicket();
        when(accountRepository.existsByPhoneHash(anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.register(
                new RegisterCommand("ticket-1", PHONE, "123456", "张三")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.PHONE_EXISTS));

        verify(accountRepository, never()).save(any());
        verifyNoInteractions(rosterGateway, eventPublisher);
    }

    @Test
    @DisplayName("注册：名册占用失败（并发同号）→ 统一 2101，不发事件、不消费验证码")
    void registerStopsWhenRosterOccupyFails() {
        stubThroughTicket();
        when(accountRepository.existsByEmailHash(anyString())).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        when(rosterGateway.occupy(anyString(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.register(
                new RegisterCommand("ticket-1", EMAIL, "123456", "张三")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.STUDENT_VERIFY_FAILED));

        verify(captchaService, never()).consume(anyString());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("注册成功：消费验证码、发布注册事件、返回令牌（事件带昵称，供审计消费方使用）")
    void registerSuccessConsumesCaptchaAndPublishesEvent() {
        stubThroughTicket();
        when(accountRepository.existsByEmailHash(anyString())).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        when(rosterGateway.occupy(anyString(), any())).thenReturn(true);
        when(tokenIssuer.issue(any(), anyString())).thenReturn("jwt-token");
        when(tokenIssuer.ttlSeconds()).thenReturn(604800L);

        LoginResult result = service.register(
                new RegisterCommand("ticket-1", EMAIL, "123456", "张三"));

        assertThat(result.token()).isEqualTo("jwt-token");
        verify(captchaService).consume(EMAIL);

        ArgumentCaptor<AccountRegisteredEvent> event = ArgumentCaptor.forClass(AccountRegisteredEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().nickname()).isEqualTo("张三");
    }

    // ── 登录的账号状态机 ─────────────────────────────────────────────────

    @Test
    @DisplayName("登录（冷静期内）：视为撤销注销——回写 ACTIVE、记审计、消费验证码，响应状态必须是 ACTIVE")
    void loginWithinCoolingRevokesDeactivation() {
        Instant future = Instant.now().plus(Duration.ofDays(3));
        Account deactivated = account(AccountStatus.DEACTIVATED, future);
        Account active = account(AccountStatus.ACTIVE, null);
        stubLoginChannel(deactivated, true);
        when(accountRepository.findById(USER_ID)).thenReturn(Optional.of(active));
        when(tokenIssuer.issue(any(), anyString())).thenReturn("jwt-token");
        when(tokenIssuer.ttlSeconds()).thenReturn(604800L);

        LoginResult result = service.login(new LoginCommand(EMAIL, "123456"));

        assertThat(result.account().getStatus()).isEqualTo(AccountStatus.ACTIVE);
        verify(accountRepository).updateStatus(USER_ID, AccountStatus.ACTIVE, null);
        verify(auditService).record(USER_ID, "ACCOUNT_REACTIVATE", "users", USER_ID, null);
        verify(captchaService).consume(EMAIL);
    }

    @Test
    @DisplayName("登录（冷静期已满）：403 / 2011 拒登，不消费验证码（后台匿名化才是收尾动作者）")
    void loginAfterCoolingExpiredIsRejected() {
        Account expired = account(AccountStatus.DEACTIVATED, Instant.now().minusSeconds(1));
        stubLoginChannel(expired, true);

        assertThatThrownBy(() -> service.login(new LoginCommand(EMAIL, "123456")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.ACCOUNT_EXPIRED));

        verify(accountRepository, never()).updateStatus(anyLong(), any(), any());
        verify(captchaService, never()).consume(anyString());
    }

    @Test
    @DisplayName("登录（封禁）：403 / 2006，与「验证码错误」区分开——封禁是明确告知的状态")
    void loginBannedAccountIsRejected() {
        stubLoginChannel(account(AccountStatus.BANNED, null), true);

        assertThatThrownBy(() -> service.login(new LoginCommand(EMAIL, "123456")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.USER_BANNED));

        verify(captchaService, never()).consume(anyString());
    }

    @Test
    @DisplayName("登录：账号存在但验证码不对 → 统一 400 / 2005（不泄露账号是否存在）")
    void loginWithWrongCaptchaFailsUnified() {
        stubLoginChannel(account(AccountStatus.ACTIVE, null), false);

        assertThatThrownBy(() -> service.login(new LoginCommand(EMAIL, "000000")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.LOGIN_FAILED));
    }

    // ── 注销 / 发帖前置 / 管理员处置 ─────────────────────────────────────

    @Test
    @DisplayName("注销：写 DEACTIVATED 且冷静期写 delete_at = now+7 天，全程审计")
    void deactivateSetsSevenDayCoolingWindow() {
        when(accountRepository.findById(USER_ID)).thenReturn(Optional.of(account(AccountStatus.ACTIVE, null)));
        Instant before = Instant.now();

        service.deactivate(USER_ID);

        Instant expected = before.plus(Duration.ofDays(7));
        ArgumentCaptor<Instant> deleteAt = ArgumentCaptor.forClass(Instant.class);
        verify(accountRepository).updateStatus(eq(USER_ID), eq(AccountStatus.DEACTIVATED), deleteAt.capture());
        assertThat(deleteAt.getValue()).isBetween(expected.minusSeconds(60), expected.plusSeconds(60));
        verify(auditService).record(USER_ID, "ACCOUNT_DEACTIVATE", "users", USER_ID, "coolingDays=7");
    }

    @Test
    @DisplayName("注销：账号不存在 → 2007，不写状态也不记审计")
    void deactivateMissingAccountIsNotFound() {
        when(accountRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivate(USER_ID))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.USER_NOT_FOUND));

        verify(accountRepository, never()).updateStatus(anyLong(), any(), any());
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("发帖前置门：封禁 → 2006、注销中 → 2013、正常 → 放行（三种状态三条出口）")
    void assertActiveForContentGatesByStatus() {
        when(accountRepository.findById(USER_ID))
                .thenReturn(Optional.of(account(AccountStatus.BANNED, null)))
                .thenReturn(Optional.of(account(AccountStatus.DEACTIVATED, Instant.now().plusSeconds(600))))
                .thenReturn(Optional.of(account(AccountStatus.ACTIVE, null)));

        assertThatThrownBy(() -> service.assertActiveForContent(USER_ID))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.USER_BANNED));
        assertThatThrownBy(() -> service.assertActiveForContent(USER_ID))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.ACCOUNT_DEACTIVATED));
        assertThatCode(() -> service.assertActiveForContent(USER_ID)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("管理员处置：超级管理员不可被封禁 → 403 / 4002，不写状态不记审计")
    void adminSetStatusRefusesSuperadmin() {
        Account superadmin = Account.rehydrate(USER_ID, EmailAddress.of("admin@campuslink.local"), null, null,
                "管理员", null, null, null, null, null, AccountRole.SUPERADMIN, AccountStatus.ACTIVE,
                true, false, null, null, null);
        when(accountRepository.findById(USER_ID)).thenReturn(Optional.of(superadmin));

        assertThatThrownBy(() -> service.adminSetStatus(1L, USER_ID, AccountStatus.BANNED))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.FORBIDDEN));

        verify(accountRepository, never()).updateStatus(anyLong(), any(), any());
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("管理员封禁：updateStatus(BANNED, null) 清掉残留 delete_at，审计动作名为 USER_BAN")
    void adminBanWritesStatusAndAuditsBan() {
        when(accountRepository.findById(USER_ID)).thenReturn(Optional.of(account(AccountStatus.ACTIVE, null)));

        service.adminBan(1L, USER_ID);

        verify(accountRepository).updateStatus(USER_ID, AccountStatus.BANNED, null);
        verify(auditService).record(1L, "USER_BAN", "users", USER_ID, null);
    }

    @Test
    @DisplayName("管理员解封：审计动作名为 USER_UNBAN（与封禁分得开，否则审计日志读不出方向）")
    void adminUnbanAuditsUnbanAction() {
        when(accountRepository.findById(USER_ID)).thenReturn(Optional.of(account(AccountStatus.BANNED, null)));

        service.adminSetStatus(1L, USER_ID, AccountStatus.ACTIVE);

        verify(accountRepository).updateStatus(eq(USER_ID), eq(AccountStatus.ACTIVE), isNull());
        verify(auditService).record(1L, "USER_UNBAN", "users", USER_ID, null);
    }

    // ---------- helpers ----------

    /** 校验码 + 票据两道门都放行的公共桩（邮箱与手机号渠道共用） */
    private void stubThroughTicket() {
        when(captchaService.verify(anyString(), anyString())).thenReturn(true);
        when(ticketStore.consume("ticket-1")).thenReturn(Optional.of(ENCRYPTED_STUDENT_ID));
        when(codec.decrypt(ENCRYPTED_STUDENT_ID)).thenReturn(STUDENT_ID);
        when(codec.hash(anyString())).thenAnswer(inv -> "H(" + inv.getArgument(0) + ")");
    }

    /** 邮箱渠道登录：查得到账号 + 验证码校验结果由用例给定 */
    private void stubLoginChannel(Account account, boolean captchaOk) {
        when(codec.hash(anyString())).thenAnswer(inv -> "H(" + inv.getArgument(0) + ")");
        when(accountRepository.findByEmailHash("H(" + EMAIL + ")")).thenReturn(Optional.of(account));
        when(captchaService.verify(eq(EMAIL), anyString())).thenReturn(captchaOk);
    }

    private static Account account(AccountStatus status, Instant deleteAt) {
        return Account.rehydrate(USER_ID, EmailAddress.of(EMAIL), null, StudentId.of(STUDENT_ID),
                "张三同学", null, null, null, null, null, AccountRole.USER, status,
                true, false, deleteAt, Instant.parse("2026-09-01T08:00:00Z"), null);
    }
}
