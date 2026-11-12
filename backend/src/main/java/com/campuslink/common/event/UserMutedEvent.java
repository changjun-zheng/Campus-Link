package com.campuslink.common.event;

import java.time.Instant;

/**
 * 管理员禁言事件（F-ADMIN-002 / CR-209）：与 {@link UserWarnedEvent} 同理放 common，避免跨上下文
 * 依赖 account 的 domain 包（G4）。
 */
public record UserMutedEvent(long operatorId, long targetUserId, Instant mutedUntil, String reason) {
}
