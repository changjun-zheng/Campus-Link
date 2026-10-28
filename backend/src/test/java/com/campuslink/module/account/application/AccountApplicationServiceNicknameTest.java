package com.campuslink.module.account.application;

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

import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 跨上下文只读方法 authorBriefsOf：整页作者昵称 + 头像必须一次查齐（不做 N+1），
 * 查不到的 id 由调用方回落文案，方法本身不编造昵称 / 头像。
 */
@ExtendWith(MockitoExtension.class)
class AccountApplicationServiceNicknameTest {

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
    @DisplayName("整页 20 个作者只查一次库（无 N+1）")
    void queriesOnceForWholePage() {
        List<Long> ids = LongStream.rangeClosed(1, 20).boxed().toList();
        when(accountRepository.findByIds(any())).thenReturn(List.of());

        service.authorBriefsOf(ids);

        verify(accountRepository, times(1)).findByIds(ids);
    }

    @Test
    @DisplayName("昵称按 id 返回；查不到的 id 不出现（缺失由调用方回落）")
    void returnsNicknamePerFoundIdAndOmitsMissingOnes() {
        when(accountRepository.findByIds(any())).thenReturn(List.of(
                account(1L, "张三"), account(3L, "王五")));

        Map<Long, AuthorBrief> briefs = service.authorBriefsOf(List.of(1L, 2L, 3L));

        assertThat(briefs).containsOnlyKeys(1L, 3L);
        assertThat(briefs.get(1L).nickname()).isEqualTo("张三");
        assertThat(briefs.get(3L).nickname()).isEqualTo("王五");
        assertThat(briefs.values()).allSatisfy(brief -> assertThat(brief.avatarUrl()).isNull());
    }

    @Test
    @DisplayName("空入参直接返回空 Map，不触达仓储")
    void emptyInputSkipsRepository() {
        assertThat(service.authorBriefsOf(List.of())).isEmpty();
        assertThat(service.authorBriefsOf(null)).isEmpty();

        verify(accountRepository, never()).findByIds(any());
    }

    @Test
    @DisplayName("头像随昵称一起返回（CR-162）：同一批查询，不额外触达仓储")
    void returnsAvatarAlongsideNickname() {
        when(accountRepository.findByIds(any())).thenReturn(List.of(account(7L, "小李", "/avatars/avatar-08-robot.png")));

        Map<Long, AuthorBrief> briefs = service.authorBriefsOf(List.of(7L));

        assertThat(briefs.get(7L).nickname()).isEqualTo("小李");
        assertThat(briefs.get(7L).avatarUrl()).isEqualTo("/avatars/avatar-08-robot.png");
        verify(accountRepository, times(1)).findByIds(any());
    }

    private static Account account(Long id, String nickname) {
        return account(id, nickname, null);
    }

    private static Account account(Long id, String nickname, String avatarUrl) {
        return Account.rehydrate(id, EmailAddress.of("u" + id + "@example.com"), null, null, nickname, avatarUrl,
                null, null, null, null, AccountRole.USER, AccountStatus.ACTIVE, true, false, null, null, null);
    }
}
