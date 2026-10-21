package com.campuslink.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 热榜配置启动即校验（CR-117，闭环问题清单 BUG-007）。
 *
 * <p><b>要挡的是哪一类错法</b>：{@code lock-ttl-seconds} 不显式释放、只靠 TTL 兜底。一旦 TTL 大于
 * {@code refresh-interval-ms}，上一轮持有的锁会覆盖下一轮 {@code tryAcquire} ⇒ 实际刷新周期被静默
 * 拉长成 TTL，日志一行不报、热榜分数悄悄变旧。这类"配置合法但组合非法"的值，单字段范围校验拦不住。
 *
 * <p><b>为什么刷新周期不绑进 {@link AppProperties}</b>：它只被 {@code @Scheduled} 的占位符消费，
 * 再绑一份就成了两个事实源（改一处另一处静默不动，见 {@link AppProperties.Hot} 的类注释）。
 * 本类只用 {@link Environment} 读同一个键做校验、不持有配置，校验完即不再引用。
 */
@Component
@RequiredArgsConstructor
public class HotConfigGuard {

    /** 仅作"键缺失时"的兜底，与 {@code HotScoreRefreshScheduler} 的占位符默认值一致（yml 恒定义该键） */
    private static final long DEFAULT_REFRESH_INTERVAL_MS = 600_000L;
    private static final long MAX_REFRESH_INTERVAL_MS = 7L * 24 * 3600 * 1000;
    private static final int MAX_WINDOW_DAYS = 365;
    private static final int MAX_BATCH_SIZE = 5_000;

    private final AppProperties props;
    private final Environment environment;

    @PostConstruct
    void validate() {
        AppProperties.Hot hot = props.getHot();
        long refreshIntervalMs = environment.getProperty(
                "campuslink.hot.refresh-interval-ms", Long.class, DEFAULT_REFRESH_INTERVAL_MS);

        List<String> violations = new ArrayList<>();
        if (refreshIntervalMs <= 0 || refreshIntervalMs > MAX_REFRESH_INTERVAL_MS) {
            violations.add("campuslink.hot.refresh-interval-ms 须在 1~" + MAX_REFRESH_INTERVAL_MS + "（当前 "
                    + refreshIntervalMs + "）");
        }
        if (hot.getLockTtlSeconds() <= 0) {
            violations.add("campuslink.hot.lock-ttl-seconds 须 > 0（当前 " + hot.getLockTtlSeconds() + "）");
        } else if (refreshIntervalMs > 0 && hot.getLockTtlSeconds() > refreshIntervalMs / 1000L) {
            // 用除法比较，避免 lockTtlSeconds*1000 在大值下溢出；语义等价于 TTL ≤ 刷新周期
            violations.add("campuslink.hot.lock-ttl-seconds（" + hot.getLockTtlSeconds()
                    + " 秒）须 ≤ 刷新周期（" + refreshIntervalMs + " ms）——锁靠 TTL 兜底，"
                    + "TTL 比周期长会把实际刷新周期变成 TTL");
        }
        if (hot.getWindowDays() < 1 || hot.getWindowDays() > MAX_WINDOW_DAYS) {
            violations.add("campuslink.hot.window-days 须在 1~" + MAX_WINDOW_DAYS + "（当前 "
                    + hot.getWindowDays() + "）");
        }
        if (hot.getBatchSize() < 1 || hot.getBatchSize() > MAX_BATCH_SIZE) {
            violations.add("campuslink.hot.batch-size 须在 1~" + MAX_BATCH_SIZE + "（当前 "
                    + hot.getBatchSize() + "）");
        }
        if (hot.getDecayPerHour() < 0) {
            violations.add("campuslink.hot.decay-per-hour 不得为负（当前 " + hot.getDecayPerHour() + "）");
        }
        if (hot.getReplyWeight() < 0 || hot.getLikeWeight() < 0 || hot.getFavoriteWeight() < 0) {
            violations.add("campuslink.hot 三个权重不得为负（reply=" + hot.getReplyWeight()
                    + " / like=" + hot.getLikeWeight() + " / favorite=" + hot.getFavoriteWeight() + "）");
        }
        if (!violations.isEmpty()) {
            throw new IllegalStateException("热榜配置非法：" + String.join("；", violations));
        }
    }
}
