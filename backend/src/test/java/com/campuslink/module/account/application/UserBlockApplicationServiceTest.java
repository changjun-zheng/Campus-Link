package com.campuslink.module.account.application;

import com.campuslink.common.audit.AuditService;
import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.account.application.cmd.BlockedUsers;
import com.campuslink.module.account.domain.gateway.AccountRepository;
import com.campuslink.module.account.domain.gateway.UserBlockRepository;
import com.campuslink.module.account.domain.model.Account;
import com.campuslink.module.account.domain.model.AccountStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserBlockApplicationServiceTest {

    @Mock
    private UserBlockRepository userBlockRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private AuditService auditService;
    @InjectMocks
    private UserBlockApplicationService service;

    @Test
    @DisplayName("拉黑：新插入记一条 USER_BLOCK 审计；重复拉黑幂等且不重复记审计")
    void blockAuditsOnlyFirstInsert() {
        Account target = activeAccount(8L);
        when(accountRepository.findById(8L)).thenReturn(Optional.of(target));
        when(userBlockRepository.block(1L, 8L)).thenReturn(true);

        service.block(1L, 8L);

        verify(auditService).record(1L, "USER_BLOCK", "users", 8L, null);

        when(userBlockRepository.block(1L, 8L)).thenReturn(false);
        service.block(1L, 8L);
        verify(auditService).record(1L, "USER_BLOCK", "users", 8L, null);
    }

    @Test
    @DisplayName("不能拉黑自己 → 400 / 1001，且不查目标、不写库")
    void blockSelfIsInvalid() {
        assertThatThrownBy(() -> service.block(1L, 1L))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));

        verifyNoInteractions(userBlockRepository, auditService, accountRepository);
    }

    @Test
    @DisplayName("目标不存在或已注销 → 404 / 2007，不写库")
    void blockMissingTargetIsNotFound() {
        when(accountRepository.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.block(1L, 9L))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.USER_NOT_FOUND));

        Account deactivated = deactivatedAccount(10L);
        when(accountRepository.findById(10L)).thenReturn(Optional.of(deactivated));
        assertThatThrownBy(() -> service.block(1L, 10L))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.USER_NOT_FOUND));

        verify(userBlockRepository, never()).block(anyLong(), anyLong());
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("解除：已存在才删并记 USER_UNBLOCK；不存在时幂等且零审计")
    void unblockAuditsOnlyWhenExisted() {
        when(userBlockRepository.exists(1L, 8L)).thenReturn(true);
        service.unblock(1L, 8L);
        verify(userBlockRepository).unblock(1L, 8L);
        verify(auditService).record(1L, "USER_UNBLOCK", "users", 8L, null);

        when(userBlockRepository.exists(1L, 9L)).thenReturn(false);
        service.unblock(1L, 9L);
        verify(userBlockRepository, never()).unblock(1L, 9L);
        verify(auditService, never()).record(1L, "USER_UNBLOCK", "users", 9L, null);
    }

    @Test
    @DisplayName("黑名单分页：按拉黑序返回 id + 昵称；已注销回落到「已注销用户」")
    void pageBlockedMapsOrderAndFallback() {
        when(userBlockRepository.countBlocked(1L)).thenReturn(2L);
        when(userBlockRepository.pageBlockedIds(1L, 0, 20)).thenReturn(List.of(8L, 9L));
        Account first = activeAccount(8L);
        Account second = deactivatedAccount(9L);
        when(accountRepository.findByIds(List.of(8L, 9L))).thenReturn(List.of(first, second));

        BlockedUsers result = service.pageBlocked(1L, 1, 20);

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.items()).extracting(BlockedUsers.BlockedUser::id).containsExactly(8L, 9L);
        assertThat(result.items().get(0).nickname()).isEqualTo("模拟学生08");
        assertThat(result.items().get(1).nickname()).isEqualTo("已注销用户");
    }

    private static Account activeAccount(long id) {
        Account account = mock(Account.class);
        org.mockito.Mockito.lenient().when(account.getId()).thenReturn(id);
        org.mockito.Mockito.lenient().when(account.getStatus()).thenReturn(AccountStatus.ACTIVE);
        org.mockito.Mockito.lenient().when(account.getNickname()).thenReturn("模拟学生%02d".formatted(id));
        return account;
    }

    private static Account deactivatedAccount(long id) {
        Account account = mock(Account.class);
        lenientStatus(account, id);
        return account;
    }

    private static void lenientStatus(Account account, long id) {
        org.mockito.Mockito.lenient().when(account.getId()).thenReturn(id);
        org.mockito.Mockito.lenient().when(account.getStatus()).thenReturn(AccountStatus.DEACTIVATED);
        org.mockito.Mockito.lenient().when(account.getNickname()).thenReturn("原昵称");
    }
}
