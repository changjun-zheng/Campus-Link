package com.campuslink.common.event;

/**
 * 管理员警告事件（F-ADMIN-002 / CR-209）。
 *
 * <p>放在 common 而非 account.domain.event：消费方是 notification 上下文，G4 只允许跨上下文依赖对方的
 * application 包，事件类型若定义在 account.domain 会被守护测试拦下。account 只负责发布，notification 的
 * infrastructure 监听并落通知——与 {@code AccountRegisteredEvent} 的上下文内监听是两种用法。
 */
public record UserWarnedEvent(long operatorId, long targetUserId, String reason) {
}
