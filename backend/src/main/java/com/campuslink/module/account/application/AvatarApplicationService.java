package com.campuslink.module.account.application;

import com.campuslink.common.audit.AuditService;
import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.account.domain.gateway.AccountRepository;
import com.campuslink.module.account.domain.gateway.AvatarImageGateway;
import com.campuslink.module.account.domain.gateway.RateLimitGateway;
import com.campuslink.module.account.domain.model.Account;
import com.campuslink.module.account.domain.model.AccountStatus;
import com.campuslink.module.account.domain.model.DefaultAvatar;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;

/**
 * 头像用例（CR-191 / F-ACC-007h）：预置选择与上传共用「限流 → 状态门 → 写路径 → 审计」。
 *
 * <p>不复用 {@link ProfileApplicationService} 的限流桶：PRD 007e 与 007h 是两个门槛，
 * 合并计数会让改资料挤压换头像的配额。审计 detail 只记 {@code source=preset|upload}，
 * 不落文件名 / 原图路径。
 *
 * <p>旧自定义头像的删除放在事务提交之后：提交前删掉旧文件而事务回滚，库里仍指向旧 URL，
 * 头像会变成 404。
 */
@Service
@RequiredArgsConstructor
public class AvatarApplicationService {

    /** 与资料编辑同档（PRD §4.3 007e 的"10 次/小时"），但使用独立的键与错误码 */
    private static final int HOURLY_LIMIT = 10;
    private static final Duration WINDOW = Duration.ofHours(1);

    private final AccountRepository accountRepository;
    private final AvatarImageGateway avatarImageGateway;
    private final RateLimitGateway rateLimitGateway;
    private final AuditService auditService;

    /** 预置头像目录（公开端点）：由后端下发，前端不硬编码，避免两处漂移 */
    public List<String> presets() {
        return DefaultAvatar.paths();
    }

    /** 选用预置头像（F-ACC-007h）：白名单外一律 2016，不接受任意 URL */
    @Transactional
    public Account selectPreset(long userId, String avatarUrl) {
        assertWithinRateLimit(userId);
        if (!DefaultAvatar.paths().contains(avatarUrl)) {
            throw new ApiException(ResultCode.AVATAR_INVALID);
        }
        Account account = requireEditableAccount(userId);
        if (!account.updateAvatar(avatarUrl)) {
            // 幂等：已是该预设 → 不发 UPDATE、不记审计（配额已消耗，空提交绕不过限流）
            return account;
        }
        accountRepository.updateAvatar(userId, avatarUrl);
        auditService.record(userId, "AVATAR_UPDATE", "users", userId, "source=preset");
        return account;
    }

    /** 上传自定义头像（F-ACC-007h）：格式 / 体积 / 像素由适配器校验，本层只做状态与限流门 */
    @Transactional
    public Account upload(long userId, byte[] raw) {
        assertWithinRateLimit(userId);
        Account account = requireEditableAccount(userId);
        String oldAvatar = account.getAvatarUrl();

        String stored = avatarImageGateway.store(userId, raw);
        if (!account.updateAvatar(stored)) {
            // UUID 生成的路径不会重复；该分支只为让幂等语义完整（不写库、不记审计）
            return account;
        }
        accountRepository.updateAvatar(userId, stored);
        auditService.record(userId, "AVATAR_UPDATE", "users", userId, "source=upload");
        deleteOldAvatarAfterCommit(oldAvatar);
        return account;
    }

    private void assertWithinRateLimit(long userId) {
        long hits = rateLimitGateway.hitAndCount("avatar:update:" + userId, WINDOW);
        if (hits > HOURLY_LIMIT) {
            throw new ApiException(ResultCode.AVATAR_UPDATE_TOO_FREQUENT);
        }
    }

    private Account requireEditableAccount(long userId) {
        Account account = accountRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ResultCode.USER_NOT_FOUND));
        if (account.getStatus() == AccountStatus.BANNED) {
            throw new ApiException(ResultCode.USER_BANNED);
        }
        return account;
    }

    private void deleteOldAvatarAfterCommit(String oldAvatar) {
        if (oldAvatar == null || oldAvatar.isBlank()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    avatarImageGateway.delete(oldAvatar);
                }
            });
        } else {
            // 单测 / 非事务调用：没有提交点可挂，立即删除
            avatarImageGateway.delete(oldAvatar);
        }
    }
}
