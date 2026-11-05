package com.campuslink.module.assistant.infrastructure.redis;

import com.campuslink.module.assistant.domain.gateway.AssistantUsageGateway.UsageDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisAssistantUsageGatewayTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private RedisAssistantUsageGateway gateway;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        gateway = new RedisAssistantUsageGateway(redis);
    }

    @Test
    @DisplayName("拿到并发锁、分钟与日配额都未超：允许并返回剩余次数")
    void allowsWhenAllGatesPass() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        stubScripts(1L, 5L);

        UsageDecision decision = gateway.acquire(7L, 20, 5, ZoneId.of("Asia/Shanghai"), Duration.ofSeconds(120));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.remainingToday()).isEqualTo(15);
        assertThat(decision.leaseToken()).isNotBlank();
    }

    @Test
    @DisplayName("并发锁已被占用：BUSY，不执行计数脚本")
    void busyWhenLockHeld() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        UsageDecision decision = gateway.acquire(7L, 20, 5, ZoneId.of("Asia/Shanghai"), Duration.ofSeconds(120));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).isEqualTo(UsageDecision.DenyReason.BUSY);
        verify(redis, never()).execute(any(RedisScript.class), anyList(), any(), any());
        verify(redis, never()).execute(any(RedisScript.class), anyList(), any());
    }

    @Test
    @DisplayName("分钟限流命中：RATE_LIMITED，并释放并发锁")
    void rateLimitedReleasesLock() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        stubScripts(-1L, 0L);

        UsageDecision decision = gateway.acquire(7L, 20, 5, ZoneId.of("Asia/Shanghai"), Duration.ofSeconds(120));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).isEqualTo(UsageDecision.DenyReason.RATE_LIMITED);
        // 分钟脚本（两个 vararg）+ 释放脚本（一个 vararg）各一次；租约 token 是随机值，不断言具体内容
        verify(redis).execute(any(RedisScript.class), anyList(), any(), any());
        verify(redis).execute(any(RedisScript.class), anyList(), any());
    }

    @Test
    @DisplayName("日配额命中：QUOTA_EXCEEDED，并释放并发锁")
    void quotaExceededReleasesLock() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        stubScripts(1L, -1L);

        UsageDecision decision = gateway.acquire(7L, 20, 5, ZoneId.of("Asia/Shanghai"), Duration.ofSeconds(120));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).isEqualTo(UsageDecision.DenyReason.QUOTA_EXCEEDED);
        // 分钟脚本 + 日配额脚本（各两个 vararg）+ 释放脚本（一个 vararg）
        verify(redis, org.mockito.Mockito.times(2)).execute(any(RedisScript.class), anyList(), any(), any());
        verify(redis).execute(any(RedisScript.class), anyList(), any());
    }

    @Test
    @DisplayName("release 只删自己的租约；token 为空时不动 Redis")
    void releaseOnlyWithToken() {
        stubScripts(0L, 0L);

        gateway.release(7L, null);
        verify(redis, never()).execute(any(RedisScript.class), anyList(), any(), any());
        verify(redis, never()).execute(any(RedisScript.class), anyList(), any());

        gateway.release(7L, "token");
        verify(redis).execute(any(RedisScript.class), anyList(), eq("token"));
    }

    /**
     * 第一次 execute 是分钟键，第二次是日配额键；用一个 Answer 按 keys 内容区分。
     * 注意释放脚本也会走 execute，调用方用例只断言"是否发生"，不依赖其返回值。
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void stubScripts(long rateResult, long quotaResult) {
        // Mockito 按 vararg 个数区分重载：分钟 / 日配额脚本传两个参数，释放脚本只传一个，
        // 因此必须分别打桩；只写一个 any() 时两个参数的调用不会命中，会静默返回 null。
        when(redis.execute(any(RedisScript.class), anyList(), any(), any())).thenAnswer(invocation -> {
            List<?> keys = invocation.getArgument(1);
            String key = keys.isEmpty() ? "" : keys.get(0).toString();
            if (key.contains(":rate:")) {
                return rateResult;
            }
            if (key.contains(":quota:")) {
                return quotaResult;
            }
            return 0L;
        });
        when(redis.execute(any(RedisScript.class), anyList(), any())).thenReturn(0L);
    }
}
