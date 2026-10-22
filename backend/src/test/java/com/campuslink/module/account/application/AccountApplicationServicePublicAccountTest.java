package com.campuslink.module.account.application;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.common.audit.AuditService;
import com.campuslink.config.AppProperties;
import static org.mockito.Mockito.mock;
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
import static org.mockito.Mockito.when;

/**
 * 他人主页的账号可见性门（{@code publicAccountOf}，F-ACC-002 本体最小版）。
 *
 * <p>
 * 三态各守一头，都是产品口径而不是实现细节：
 * ① 不存在与已注销（{@code DEACTIVATED}）**必须同一个码**（2007 / 404）——分得开就等于给外人一个
 * 按 id 枚举"这个学号注册过没有、注销了没有"的探针，与帖子侧把"不存在 / 已删 / 未发布"全归 3001 同一条纪律；
 * ② 被封禁（{@code BANNED}）**必须照常返回**——封禁限的是发言与登录，全站列表 / 搜索从不按作者状态过滤，
 * 主页若单独消失，列表里那个作者名就成了点不开的死链；
 * ③ 这道门与 {@link AccountApplicationService#accountOf} 分开存在：后者答"这个 id 有没有账号"
 * （关注 / 令牌校验用，注销中的账号仍是关注关系的一方），把注销门塞进 {@code accountOf} 会顺带改掉关注链路的语义。
 */
@ExtendWith(MockitoExtension.class)
class AccountApplicationServicePublicAccountTest {

    private static final long USER_ID = 42L;

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
                codec, tokenIssuer, rateLimitGateway, mock(AuditService.class), new AppProperties(), eventPublisher,
                mock(ModerationApplicationService.class));
    }

    @Test
    @DisplayName("账号不存在 → 2007 / 404")
    void missingAccountIsNotFound() {
        when(accountRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.publicAccountOf(USER_ID))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.USER_NOT_FOUND));
    }

    @Test
    @DisplayName("已注销（DEACTIVATED）→ 与「不存在」同一个码，不对外区分")
    void deactivatedAccountIsNotFound() {
        when(accountRepository.findById(USER_ID)).thenReturn(Optional.of(account(AccountStatus.DEACTIVATED)));

        assertThatThrownBy(() -> service.publicAccountOf(USER_ID))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.USER_NOT_FOUND));
    }

    @Test
    @DisplayName("⚠️ 反证守门：被封禁（BANNED）的账号主页照常可读——封禁不等于把已发布内容从站内抹掉")
    void bannedAccountIsStillPublic() {
        when(accountRepository.findById(USER_ID)).thenReturn(Optional.of(account(AccountStatus.BANNED)));

        assertThat(service.publicAccountOf(USER_ID).getStatus()).isEqualTo(AccountStatus.BANNED);
    }

    @Test
    @DisplayName("正常账号 → 原样返回聚合（展示列由调用方挑）")
    void activeAccountIsReturned() {
        Account active = account(AccountStatus.ACTIVE);
        when(accountRepository.findById(USER_ID)).thenReturn(Optional.of(active));

        assertThat(service.publicAccountOf(USER_ID)).isSameAs(active);
    }

    private static Account account(AccountStatus status) {
        return Account.rehydrate(USER_ID, EmailAddress.of("dev-stu-01@dev.campuslink.local"), null, null, "张三同学", null,
                null, "软件工程", null, "在写代码", AccountRole.USER, status, true, false, null,
                Instant.parse("2026-09-01T08:00:00Z"), null);
    }
}
