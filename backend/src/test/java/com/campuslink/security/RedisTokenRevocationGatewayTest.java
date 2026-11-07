package com.campuslink.security;

import com.campuslink.config.AppProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** JWT 吊销水位（CR-208）：键前缀、TTL 与秒级比较口径。 */
@ExtendWith(MockitoExtension.class)
class RedisTokenRevocationGatewayTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> ops;

    @Test
    @DisplayName("写水位：键带 campuslink: 前缀，值为 epoch 秒，TTL = JWT 有效期 + 1 小时")
    void revokeWritesPrefixedWatermarkWithTtl() {
        when(redis.opsForValue()).thenReturn(ops);
        RedisTokenRevocationGateway gateway = gateway();

        gateway.revokeAllBefore(42L, Instant.ofEpochSecond(1_700_000_000L));

        verify(ops).set(eq("campuslink:auth:revoked-before:42"), eq("1700000000"),
                eq(Duration.ofSeconds(gatewayTtlSeconds()).plusHours(1)));
    }

    @Test
    @DisplayName("判撤销：签发秒早于或等于水位都算（同秒宁可误伤新 token，也不放过旧 token）")
    void isRevokedComparesAtSecondPrecision() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get("campuslink:auth:revoked-before:42")).thenReturn("1700000000");
        RedisTokenRevocationGateway gateway = gateway();

        assertThat(gateway.isRevoked(42L, Instant.ofEpochSecond(1_699_999_999L))).isTrue();
        assertThat(gateway.isRevoked(42L, Instant.ofEpochSecond(1_700_000_000L))).isTrue();
        assertThat(gateway.isRevoked(42L, Instant.ofEpochSecond(1_700_000_001L))).isFalse();
    }

    @Test
    @DisplayName("无水位 / 水位值损坏：均不判撤销（失焦时 fail-open）")
    void missingOrBrokenWatermarkIsNotRevoked() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenReturn(null, "not-a-number");
        RedisTokenRevocationGateway gateway = gateway();

        assertThat(gateway.isRevoked(42L, Instant.EPOCH)).isFalse();
        assertThat(gateway.isRevoked(42L, Instant.EPOCH)).isFalse();
    }

    private RedisTokenRevocationGateway gateway() {
        AppProperties props = new AppProperties();
        props.getJwt().setSecret(SECRET);
        return new RedisTokenRevocationGateway(redis, new JwtService(props));
    }

    private static long gatewayTtlSeconds() {
        AppProperties props = new AppProperties();
        props.getJwt().setSecret(SECRET);
        return new JwtService(props).ttlSeconds();
    }
}
