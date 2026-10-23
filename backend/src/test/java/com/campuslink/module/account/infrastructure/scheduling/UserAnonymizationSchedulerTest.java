package com.campuslink.module.account.infrastructure.scheduling;

import com.campuslink.common.audit.AuditService;
import com.campuslink.module.account.domain.gateway.AccountRepository;
import com.campuslink.module.account.domain.model.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 注销匿名化定时任务（F-ACC-003 / CR-082 / CR-127）：到期账号逐条匿名化并审计；无到期账号零副作用。
 *
 * <p>字段清理由 {@code AccountRepositoryImplTest#anonymizeClearsAllSensitiveColumns} 守；
 * 本类守调度层「该不该调、调几次、审计有没有跟上」。
 */
@ExtendWith(MockitoExtension.class)
class UserAnonymizationSchedulerTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private AuditService auditService;

    private UserAnonymizationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new UserAnonymizationScheduler(accountRepository, auditService);
    }

    @Test
    @DisplayName("冷静期满：逐账号匿名化并逐条审计")
    void anonymizesEachDueAccountAndAudits() {
        Account first = mock(Account.class);
        when(first.getId()).thenReturn(1L);
        Account second = mock(Account.class);
        when(second.getId()).thenReturn(2L);
        when(accountRepository.findExpiredDeactivations(any())).thenReturn(List.of(first, second));

        scheduler.anonymizeExpired();

        verify(accountRepository).anonymize(1L, "已注销用户");
        verify(accountRepository).anonymize(2L, "已注销用户");
        verify(auditService).record(1L, "ACCOUNT_ANONYMIZE", "users", 1L, null);
        verify(auditService).record(2L, "ACCOUNT_ANONYMIZE", "users", 2L, null);
    }

    @Test
    @DisplayName("无到期账号：零匿名化、零审计")
    void noDueAccountsDoesNothing() {
        when(accountRepository.findExpiredDeactivations(any())).thenReturn(List.of());

        scheduler.anonymizeExpired();

        verify(accountRepository, never()).anonymize(anyLong(), anyString());
        verifyNoInteractions(auditService);
    }
}
