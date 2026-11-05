package com.campuslink.module.assistant.infrastructure.redis;

import com.campuslink.common.redis.RedisKeys;
import com.campuslink.module.assistant.domain.gateway.AssistantUsageGateway;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Redis 实现的三道闸（CR-200）：并发锁 → 分钟限流 → 日配额。
 *
 * <p>顺序是先便宜后昂贵：并发锁挡住同一用户的重复点击；分钟限流挡住脚本刷；
 * 日配额最后扣，只有在真正会调用模型时才消耗。限额判断与自增在同一段 Lua 里完成，
 * 避免"先读后写"在并发下多放行。
 */
@Component
public class RedisAssistantUsageGateway implements AssistantUsageGateway {

    /** ARGV[1]=limit, ARGV[2]=ttlSeconds；达到上限返回 -1，不消耗计数。 */
    private static final DefaultRedisScript<Long> HIT_LIMITED = new DefaultRedisScript<>("""
            local count = tonumber(redis.call('GET', KEYS[1]) or '0')
            if count >= tonumber(ARGV[1]) then return -1 end
            count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[2]) end
            return count
            """, Long.class);

    private static final DefaultRedisScript<Long> RELEASE_IF_OWNER = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;

    public RedisAssistantUsageGateway(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public UsageDecision acquire(long userId, int dailyLimit, int perMinuteLimit, ZoneId quotaZone,
            Duration leaseTtl) {
        String leaseToken = UUID.randomUUID().toString();
        String busyKey = key("assistant:busy:" + userId);
        Boolean locked = redis.opsForValue().setIfAbsent(busyKey, leaseToken, leaseTtl);
        if (!Boolean.TRUE.equals(locked)) {
            return UsageDecision.denied(UsageDecision.DenyReason.BUSY);
        }

        try {
            long minute = Instant.now().getEpochSecond() / 60;
            Long rate = redis.execute(HIT_LIMITED,
                    List.of(key("assistant:rate:" + userId + ":" + minute)),
                    String.valueOf(Math.max(1, perMinuteLimit)), "70");
            if (rate == null || rate < 0) {
                release(userId, leaseToken);
                return UsageDecision.denied(UsageDecision.DenyReason.RATE_LIMITED);
            }

            Instant now = Instant.now();
            ZonedDateTime end = now.atZone(quotaZone).toLocalDate().plusDays(1)
                    .atStartOfDay(quotaZone).plusHours(1);
            long ttlSeconds = Math.max(60L, Duration.between(now, end.toInstant()).getSeconds());
            String day = LocalDate.now(quotaZone).format(DateTimeFormatter.BASIC_ISO_DATE);
            Long count = redis.execute(HIT_LIMITED,
                    List.of(key("assistant:quota:" + userId + ":" + day)),
                    String.valueOf(Math.max(1, dailyLimit)), String.valueOf(ttlSeconds));
            if (count == null || count < 0) {
                release(userId, leaseToken);
                return UsageDecision.denied(UsageDecision.DenyReason.QUOTA_EXCEEDED);
            }
            return UsageDecision.allowed((int) Math.max(0L, dailyLimit - count), leaseToken);
        } catch (RuntimeException e) {
            release(userId, leaseToken);
            throw e;
        }
    }

    @Override
    public void release(long userId, String leaseToken) {
        if (leaseToken == null || leaseToken.isBlank()) {
            return;
        }
        redis.execute(RELEASE_IF_OWNER, List.of(key("assistant:busy:" + userId)), leaseToken);
    }

    private String key(String logicalKey) {
        return RedisKeys.of(logicalKey);
    }
}
