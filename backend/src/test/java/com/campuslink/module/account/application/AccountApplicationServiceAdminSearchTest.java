package com.campuslink.module.account.application;

import com.campuslink.common.audit.AuditService;
import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.common.security.TokenRevocationGateway;
import com.campuslink.config.AppProperties;
import com.campuslink.module.account.application.cmd.AdminUserSearchResult;
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
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 后台用户检索（CR-211）：空 / 超长关键字拒绝；纯数字 id 优先且与昵称结果去重；
 * 昵称结果按 id 倒序；51 条截断为 50 条并明示 truncated。
 */
@ExtendWith(MockitoExtension.class)
class AccountApplicationServiceAdminSearchTest {

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
                mock(ModerationApplicationService.class), mock(TokenRevocationGateway.class));
    }

    @Test
    @DisplayName("空白关键字 → 1001，且不触达仓储")
    void blankKeywordIsRejected() {
        assertThatThrownBy(() -> service.adminSearchUsers("  "))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));
        assertThatThrownBy(() -> service.adminSearchUsers(null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));
        verifyNoInteractions(accountRepository);
    }

    @Test
    @DisplayName("关键字超过 50 字 → 1001，且不触达仓储")
    void tooLongKeywordIsRejected() {
        assertThatThrownBy(() -> service.adminSearchUsers("张".repeat(51)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));
        verifyNoInteractions(accountRepository);
    }

    @Test
    @DisplayName("纯数字关键字：id 精确命中排首位，昵称命中按 id 倒序且与精确命中去重")
    void numericKeywordPutsExactIdFirstAndDeduplicates() {
        when(accountRepository.findById(7L)).thenReturn(Optional.of(account(7L, "7")));
        when(accountRepository.findIdsByNicknameLike("7", 51)).thenReturn(List.of(9L, 7L, 3L));
        when(accountRepository.findByIds(List.of(9L, 7L, 3L))).thenReturn(List.of(
                account(3L, "老用户"), account(7L, "7"), account(9L, "新用户")));

        AdminUserSearchResult result = service.adminSearchUsers(" 7 ");

        assertThat(result.items()).extracting(AdminUserSearchResult.Item::id)
                .containsExactly(7L, 9L, 3L);
        assertThat(result.truncated()).isFalse();
        verify(accountRepository).findById(7L);
        verify(accountRepository).findIdsByNicknameLike("7", 51);
    }

    @Test
    @DisplayName("昵称关键字：结果按 id 倒序，角色 / 状态 / 禁言截止 / 注册时间原样透出")
    void nicknameSearchReturnsAccountState() {
        Instant mutedUntil = Instant.parse("2026-11-11T00:00:00Z");
        Instant createdAt = Instant.parse("2026-10-01T00:00:00Z");
        Account muted = account(2L, "张三", AccountRole.OPS, AccountStatus.BANNED, mutedUntil, createdAt);
        when(accountRepository.findIdsByNicknameLike("张", 51)).thenReturn(List.of(1L, 2L));
        when(accountRepository.findByIds(List.of(1L, 2L))).thenReturn(List.of(
                account(1L, "张三丰"), muted));

        AdminUserSearchResult result = service.adminSearchUsers("张");

        assertThat(result.items()).extracting(AdminUserSearchResult.Item::id)
                .containsExactly(2L, 1L);
        AdminUserSearchResult.Item first = result.items().get(0);
        assertThat(first.nickname()).isEqualTo("张三");
        assertThat(first.role()).isEqualTo(AccountRole.OPS);
        assertThat(first.status()).isEqualTo(AccountStatus.BANNED);
        assertThat(first.mutedUntil()).isEqualTo(mutedUntil);
        assertThat(first.createdAt()).isEqualTo(createdAt);
        assertThat(result.truncated()).isFalse();
        verify(accountRepository, never()).findById(any());
    }

    @Test
    @DisplayName("昵称命中 51 条：只取前 50 条并标记 truncated")
    void overLimitIsTruncatedToFifty() {
        List<Long> ids = LongStream.rangeClosed(1, 51).boxed().toList();
        List<Account> accounts = ids.subList(0, 50).stream()
                .map(id -> account(id, "同名" + id))
                .toList();
        when(accountRepository.findIdsByNicknameLike("同名", 51)).thenReturn(ids);
        when(accountRepository.findByIds(ids.subList(0, 50))).thenReturn(accounts);

        AdminUserSearchResult result = service.adminSearchUsers("同名");

        assertThat(result.items()).hasSize(50);
        assertThat(result.truncated()).isTrue();
        assertThat(result.items()).extracting(AdminUserSearchResult.Item::id)
                .startsWith(50L).endsWith(1L);
    }

    @Test
    @DisplayName("精确 id 与 50 条昵称命中合并后仍限 50 条，并标记 truncated")
    void exactIdPlusFiftyNicknameMatchesStillCapsAtFifty() {
        List<Long> nicknameIds = LongStream.rangeClosed(1, 50).boxed().toList();
        when(accountRepository.findById(99L)).thenReturn(Optional.of(account(99L, "同名99")));
        when(accountRepository.findIdsByNicknameLike("99", 51)).thenReturn(nicknameIds);
        when(accountRepository.findByIds(nicknameIds)).thenReturn(
                nicknameIds.stream().map(id -> account(id, "同名" + id)).toList());

        AdminUserSearchResult result = service.adminSearchUsers("99");

        assertThat(result.items()).hasSize(50);
        assertThat(result.items().get(0).id()).isEqualTo(99L);
        assertThat(result.truncated()).isTrue();
    }

    @Test
    @DisplayName("查不到 → 空列表且不报错")
    void missingUserIsEmpty() {
        when(accountRepository.findIdsByNicknameLike("nobody", 51)).thenReturn(List.of());
        when(accountRepository.findByIds(List.of())).thenReturn(List.of());

        AdminUserSearchResult result = service.adminSearchUsers("nobody");

        assertThat(result.items()).isEmpty();
        assertThat(result.truncated()).isFalse();
    }

    @Test
    @DisplayName("19 位溢出 long 的数字串按昵称继续查，不判参数错误")
    void overflowingDigitsFallBackToNickname() {
        when(accountRepository.findIdsByNicknameLike("9999999999999999999", 51)).thenReturn(List.of());
        when(accountRepository.findByIds(List.of())).thenReturn(List.of());

        AdminUserSearchResult result = service.adminSearchUsers("9999999999999999999");

        assertThat(result.items()).isEmpty();
        assertThat(result.truncated()).isFalse();
        verify(accountRepository, never()).findById(any());
    }

    private static Account account(Long id, String nickname) {
        return account(id, nickname, AccountRole.USER, AccountStatus.ACTIVE, null, null);
    }

    private static Account account(Long id, String nickname, AccountRole role, AccountStatus status,
                                   Instant mutedUntil, Instant createdAt) {
        return Account.rehydrate(id, EmailAddress.of("u" + id + "@example.com"), null, null, nickname,
                null, null, null, null, null, role, status, mutedUntil, true, false, null,
                createdAt, null);
    }
}
