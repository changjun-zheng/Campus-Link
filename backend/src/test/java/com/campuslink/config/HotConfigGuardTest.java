package com.campuslink.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 热榜配置启动即校验（CR-117，闭环问题清单 BUG-007）。
 *
 * <p><b>要守的不是单字段范围，而是组合关系</b>：{@code lock-ttl-seconds} 与
 * {@code refresh-interval-ms} 单看都合法、组合起来却会让实际刷新周期静默变成 TTL。
 * 这里不启 Spring 上下文（同 {@code ProductionSafetyGuardTest} 的口径），直接构造
 * {@link AppProperties} 与 {@link StandardEnvironment} 断言违规清单。
 */
class HotConfigGuardTest {

    private static final String INTERVAL = "campuslink.hot.refresh-interval-ms";

    private static StandardEnvironment envWithInterval(long intervalMs) {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test", Map.of(INTERVAL, String.valueOf(intervalMs))));
        return env;
    }

    private static HotConfigGuard guard(AppProperties props, long intervalMs) {
        return new HotConfigGuard(props, envWithInterval(intervalMs));
    }

    @Test
    @DisplayName("默认组合（TTL 300s / 周期 600000ms）通过")
    void defaultCombinationPasses() {
        assertThatCode(() -> guard(new AppProperties(), 600_000L).validate())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("TTL 恰等于刷新周期时通过（边界取 ≤，不是 <）")
    void ttlEqualToIntervalPasses() {
        AppProperties props = new AppProperties();
        props.getHot().setLockTtlSeconds(600L);
        assertThatCode(() -> guard(props, 600_000L).validate()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("TTL 大于刷新周期 → 启动即失败，消息点名字段与后果")
    void ttlLongerThanIntervalFails() {
        AppProperties props = new AppProperties();
        props.getHot().setLockTtlSeconds(601L);
        assertThatThrownBy(() -> guard(props, 600_000L).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lock-ttl-seconds")
                .hasMessageContaining("须 ≤ 刷新周期");
    }

    @Test
    @DisplayName("窗口天数 / 批大小越界 → 启动即失败")
    void windowAndBatchBoundsFail() {
        AppProperties zeroWindow = new AppProperties();
        zeroWindow.getHot().setWindowDays(0);
        assertThatThrownBy(() -> guard(zeroWindow, 600_000L).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("window-days");

        AppProperties oversizedBatch = new AppProperties();
        oversizedBatch.getHot().setBatchSize(5_001);
        assertThatThrownBy(() -> guard(oversizedBatch, 600_000L).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("batch-size");
    }

    @Test
    @DisplayName("负权重 / 负衰减率 → 启动即失败")
    void negativeWeightsAndDecayFail() {
        AppProperties negativeWeight = new AppProperties();
        negativeWeight.getHot().setLikeWeight(-1d);
        assertThatThrownBy(() -> guard(negativeWeight, 600_000L).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("权重不得为负");

        AppProperties negativeDecay = new AppProperties();
        negativeDecay.getHot().setDecayPerHour(-0.1d);
        assertThatThrownBy(() -> guard(negativeDecay, 600_000L).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("decay-per-hour");
    }

    @Test
    @DisplayName("刷新周期非正 → 启动即失败")
    void nonPositiveIntervalFails() {
        assertThatThrownBy(() -> guard(new AppProperties(), 0L).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("refresh-interval-ms");
    }
}
