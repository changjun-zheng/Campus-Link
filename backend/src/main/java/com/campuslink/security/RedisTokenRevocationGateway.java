package com.campuslink.security;

import com.campuslink.common.redis.RedisKeys;
import com.campuslink.common.security.TokenRevocationGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Redis 实现（CR-208）：每个用户一个「已撤销至」水位，存 epoch 秒。
 *
 * <p>TTL 取 JWT 有效期 + 1 小时：水位过期时，所有受影响的 token 也早已自然过期，
 * 键不会无限堆积。同一用户重复吊销直接覆盖为更晚的水位；调用方只会在真实状态变更后写入。
 */
@Component
@RequiredArgsConstructor
public class RedisTokenRevocationGateway implements TokenRevocationGateway {

    private static final String LOGICAL_KEY_PREFIX = "auth:revoked-before:";

    private final StringRedisTemplate redis;
    private final JwtService jwtService;

    @Override
    public void revokeAllBefore(long userId, Instant cutoff) {
        redis.opsForValue().set(key(userId), String.valueOf(cutoff.getEpochSecond()),
                Duration.ofSeconds(jwtService.ttlSeconds()).plusHours(1));
    }

    @Override
    public boolean isRevoked(long userId, Instant issuedAt) {
        String watermark = redis.opsForValue().get(key(userId));
        if (watermark == null) {
            return false;
        }
        try {
            // <=：同一秒内的 token 也判撤销——JWT 的 iat 只到秒，无法区分先后；
            // 宁可让极少数同秒新 token 失效（再登录一次），也不放过吊销前的旧 token
            return issuedAt.getEpochSecond() <= Long.parseLong(watermark);
        } catch (NumberFormatException ignored) {
            // 键值被外部写坏时不做吊销判断（fail-open），与 Redis 不可用时的取向一致
            return false;
        }
    }

    private static String key(long userId) {
        return RedisKeys.of(LOGICAL_KEY_PREFIX + userId);
    }
}
