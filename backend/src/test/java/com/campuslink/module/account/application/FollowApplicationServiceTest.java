package com.campuslink.module.account.application;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.account.application.cmd.FollowUsers;
import com.campuslink.module.account.domain.gateway.AccountRepository;
import com.campuslink.module.account.domain.gateway.FollowRepository;
import com.campuslink.module.account.domain.model.Account;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 粉丝 / 关注列表用例（F-SOC-002 补齐 / CR-130）。
 *
 * <p>守四条口径：① 目标不存在或已注销 → 2007 且**不触达关注表**（存在性门在最前）；
 * ② 条目按仓储返回的 id 顺序组装，资料批量取、不逐条查；③ 分页归一（page&lt;1、size 越界）
 * 与 offset = (page-1)*size；④ 粉丝 / 关注两侧走各自的端口方法，不互相冒充。
 *
 * <p>SQL 侧的"已注销剔除 / viewer 拉黑剔除 / total 同源"不在本类证明——那三条由
 * {@code FollowRepositoryImplTest} 读 {@code @Select} 注解文本守着。
 */
@ExtendWith(MockitoExtension.class)
class FollowApplicationServiceTest {

    private static final long VIEWER_ID = 1L;
    private static final long TARGET_ID = 7L;

    @Mock
    private FollowRepository followRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private AccountApplicationService accountApplicationService;
    @Mock
    private UserBlockApplicationService userBlockApplicationService;
    @InjectMocks
    private FollowApplicationService service;

    @Test
    @DisplayName("粉丝列表：目标不存在或已注销 → 404 / 2007，且不查关注表")
    void pageFollowersMissingTargetIsNotFound() {
        when(accountApplicationService.publicAccountOf(9L))
                .thenThrow(new ApiException(ResultCode.USER_NOT_FOUND));

        assertThatThrownBy(() -> service.pageFollowers(VIEWER_ID, 9L, 1, 20))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.USER_NOT_FOUND));

        verifyNoInteractions(followRepository, accountRepository);
    }

    @Test
    @DisplayName("关注列表：目标不存在或已注销 → 404 / 2007，且不查关注表")
    void pageFollowingMissingTargetIsNotFound() {
        when(accountApplicationService.publicAccountOf(9L))
                .thenThrow(new ApiException(ResultCode.USER_NOT_FOUND));

        assertThatThrownBy(() -> service.pageFollowing(VIEWER_ID, 9L, 1, 20))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.USER_NOT_FOUND));

        verifyNoInteractions(followRepository, accountRepository);
    }

    @Test
    @DisplayName("粉丝列表：按仓储 id 顺序组装 id / 昵称 / 专业 / 签名，total 与分页原样回显")
    void pageFollowersAssemblesProfilesInOrder() {
        Account eight = account(8L, "学生八");
        Account nine = account(9L, "学生九");
        when(followRepository.countFollowers(TARGET_ID, VIEWER_ID)).thenReturn(2L);
        when(followRepository.pageFollowerIds(TARGET_ID, VIEWER_ID, 0, 20)).thenReturn(List.of(8L, 9L));
        // 账号表返回顺序与 id 顺序相反，出参仍按关注序（不能被批量查询的打乱）
        when(accountRepository.findByIds(List.of(8L, 9L))).thenReturn(List.of(nine, eight));

        FollowUsers result = service.pageFollowers(VIEWER_ID, TARGET_ID, 1, 20);

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.items()).extracting(FollowUsers.FollowUser::id).containsExactly(8L, 9L);
        assertThat(result.items().get(0).nickname()).isEqualTo("学生八");
        assertThat(result.items().get(0).avatarUrl()).isEqualTo("/avatars/avatar-08-x.png");
        assertThat(result.items().get(0).major()).isEqualTo("计算机科学与技术");
        assertThat(result.items().get(1).bio()).isEqualTo("签名");
    }

    @Test
    @DisplayName("关注列表：走 followee 侧端口与计数，不混用粉丝侧方法")
    void pageFollowingUsesFolloweeSide() {
        Account eight = account(8L, "学生八");
        when(followRepository.countFollowing(TARGET_ID, VIEWER_ID)).thenReturn(1L);
        when(followRepository.pageFolloweeIds(TARGET_ID, VIEWER_ID, 0, 20)).thenReturn(List.of(8L));
        when(accountRepository.findByIds(List.of(8L))).thenReturn(List.of(eight));

        FollowUsers result = service.pageFollowing(VIEWER_ID, TARGET_ID, 1, 20);

        assertThat(result.items()).extracting(FollowUsers.FollowUser::id).containsExactly(8L);
        assertThat(result.total()).isEqualTo(1);
        verify(followRepository, never()).pageFollowerIds(anyLong(), anyLong(), anyInt(), anyInt());
        verify(followRepository, never()).countFollowers(anyLong(), anyLong());
    }

    @Test
    @DisplayName("分页归一：size 超上限 → 100；offset = (page-1)*size")
    void paginationClampsSizeAndComputesOffset() {
        when(followRepository.countFollowers(TARGET_ID, VIEWER_ID)).thenReturn(0L);
        when(followRepository.pageFollowerIds(TARGET_ID, VIEWER_ID, 200, 100)).thenReturn(List.of());

        FollowUsers result = service.pageFollowers(VIEWER_ID, TARGET_ID, 3, 999);

        assertThat(result.page()).isEqualTo(3);
        assertThat(result.size()).isEqualTo(100);
        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isZero();
        // 空页不查账号表
        verify(accountRepository, never()).findByIds(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("分页归一：page<1 → 1、size<1 → 1")
    void paginationFloorsPageAndSize() {
        when(followRepository.countFollowers(TARGET_ID, VIEWER_ID)).thenReturn(0L);
        when(followRepository.pageFollowerIds(TARGET_ID, VIEWER_ID, 0, 1)).thenReturn(List.of());

        FollowUsers result = service.pageFollowers(VIEWER_ID, TARGET_ID, -3, 0);

        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("账号批量取缺失：条目跳过、total 保留（并发注销的兜底，不改总数口径）")
    void missingAccountRowsAreSkipped() {
        Account eight = account(8L, "学生八");
        when(followRepository.countFollowers(TARGET_ID, VIEWER_ID)).thenReturn(2L);
        when(followRepository.pageFollowerIds(TARGET_ID, VIEWER_ID, 0, 20)).thenReturn(List.of(8L, 9L));
        when(accountRepository.findByIds(List.of(8L, 9L))).thenReturn(List.of(eight));

        FollowUsers result = service.pageFollowers(VIEWER_ID, TARGET_ID, 1, 20);

        assertThat(result.items()).hasSize(1);
        assertThat(result.total()).isEqualTo(2);
    }

    private static Account account(long id, String nickname) {
        Account account = mock(Account.class);
        lenient().when(account.getId()).thenReturn(id);
        lenient().when(account.getNickname()).thenReturn(nickname);
        lenient().when(account.getAvatarUrl()).thenReturn(String.format("/avatars/avatar-%02d-x.png", id));
        lenient().when(account.getMajor()).thenReturn("计算机科学与技术");
        lenient().when(account.getBio()).thenReturn("签名");
        return account;
    }
}
