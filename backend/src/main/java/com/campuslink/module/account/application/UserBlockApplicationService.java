package com.campuslink.module.account.application;

import com.campuslink.common.audit.AuditService;
import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.account.application.cmd.BlockedUsers;
import com.campuslink.module.account.domain.gateway.AccountRepository;
import com.campuslink.module.account.domain.gateway.UserBlockRepository;
import com.campuslink.module.account.domain.model.Account;
import com.campuslink.module.account.domain.model.AccountStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用户拉黑 / 屏蔽（F-SOC-004 / CR-129）：单向关系、被拉黑者无感知。
 *
 * <p>读侧过滤、互动阻断与通知抑制分别由 forum / notification 调用本服务的
 * {@link #blockedIdsOf(long)} / {@link #isBlocked(long, long)} 实现——跨上下文只调 application（ADR-012）。
 */
@Service
@RequiredArgsConstructor
public class UserBlockApplicationService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String NICKNAME_FALLBACK = "已注销用户";

    private final UserBlockRepository userBlockRepository;
    private final AccountRepository accountRepository;
    private final AuditService auditService;

    @Transactional
    public void block(long actorId, long targetId) {
        if (actorId == targetId) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
        accountRepository.findById(targetId)
                .filter(account -> account.getStatus() != AccountStatus.DEACTIVATED)
                .orElseThrow(() -> new ApiException(ResultCode.USER_NOT_FOUND));
        // 幂等重放不记审计：只有真的新插入才留痕
        if (userBlockRepository.block(actorId, targetId)) {
            auditService.record(actorId, "USER_BLOCK", "users", targetId, null);
        }
    }

    @Transactional
    public void unblock(long actorId, long targetId) {
        if (actorId == targetId) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
        if (userBlockRepository.exists(actorId, targetId)) {
            userBlockRepository.unblock(actorId, targetId);
            auditService.record(actorId, "USER_UNBLOCK", "users", targetId, null);
        }
    }

    public boolean isBlocked(long actorId, long targetId) {
        return userBlockRepository.exists(actorId, targetId);
    }

    /** 读侧过滤用：调用方一次取集合，不在列表 / 搜索里逐条查（与关注 Feed 的批量口径一致） */
    public Set<Long> blockedIdsOf(long actorId) {
        return userBlockRepository.blockedIdsOf(actorId);
    }

    public BlockedUsers pageBlocked(long actorId, int page, int size) {
        int currentPage = Math.max(page, 1);
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        long total = userBlockRepository.countBlocked(actorId);
        List<Long> ids = userBlockRepository.pageBlockedIds(actorId, (currentPage - 1) * pageSize, pageSize);
        Map<Long, Account> accounts = accountRepository.findByIds(ids).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));
        List<BlockedUsers.BlockedUser> items = ids.stream()
                .map(id -> {
                    Account account = accounts.get(id);
                    String nickname = account == null || account.getStatus() == AccountStatus.DEACTIVATED
                            ? NICKNAME_FALLBACK : account.getNickname();
                    return new BlockedUsers.BlockedUser(id, nickname);
                })
                .toList();
        return new BlockedUsers(items, total);
    }
}
