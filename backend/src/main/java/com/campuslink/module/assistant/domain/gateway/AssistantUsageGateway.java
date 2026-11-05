package com.campuslink.module.assistant.domain.gateway;

import java.time.Duration;
import java.time.ZoneId;

/**
 * AI 用量端口（CR-200）：日配额、分钟限流与单用户并发锁在同一处原子判断。
 *
 * <p>三者的拒绝原因必须分开，前端才能给出不同文案：配额用完 vs 请求太快 vs 上一条还在生成。
 * 获得配额后返回租约 token，释放时校验 token，避免旧请求误删新请求的并发锁。
 */
public interface AssistantUsageGateway {

    UsageDecision acquire(long userId, int dailyLimit, int perMinuteLimit, ZoneId quotaZone, Duration leaseTtl);

    void release(long userId, String leaseToken);

    record UsageDecision(boolean allowed, DenyReason reason, int remainingToday, String leaseToken) {

        public enum DenyReason {
            RATE_LIMITED,
            QUOTA_EXCEEDED,
            BUSY
        }

        public static UsageDecision allowed(int remainingToday, String leaseToken) {
            return new UsageDecision(true, null, remainingToday, leaseToken);
        }

        public static UsageDecision denied(DenyReason reason) {
            return new UsageDecision(false, reason, 0, null);
        }
    }
}
