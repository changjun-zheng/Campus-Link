package com.campuslink.module.forum.infrastructure.search;

import com.campuslink.config.AppProperties;
import com.campuslink.module.forum.application.SearchIndexApplicationService;
import com.campuslink.module.forum.domain.gateway.PostSearchIndexGateway;
import com.campuslink.module.forum.domain.gateway.ScheduledLockGateway;
import com.campuslink.module.forum.domain.gateway.SearchSyncStateGateway;
import com.campuslink.module.forum.domain.model.SearchSyncCursor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * 搜索索引同步触发器（CR-181）：启动后首轮 + 周期增量；游标缺失或索引为空时改走全量重建。
 *
 * <p>入站适配器（时钟触发）→ application 用例，与 {@code HotScoreRefreshScheduler} 同范式。
 * 周期 / 首轮延迟只走占位符、不绑 AppProperties；锁与 Redis 游标由端口适配器处理，本类不感知 Redis。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SearchIndexScheduler {

    private static final String LOCK_KEY = "search:sync:lock";

    private final AppProperties props;
    private final SearchIndexApplicationService indexService;
    private final PostSearchIndexGateway indexGateway;
    private final SearchSyncStateGateway stateGateway;
    private final ScheduledLockGateway scheduledLock;

    @Scheduled(fixedDelayString = "${campuslink.search.sync-interval-ms:30000}",
            initialDelayString = "${campuslink.search.sync-initial-delay-ms:15000}")
    public void sync() {
        if (props.getSearch().getEngine() != AppProperties.Search.Engine.MEILISEARCH) {
            return;
        }
        Duration lockTtl = Duration.ofSeconds(props.getSearch().getLockTtlSeconds());
        if (!scheduledLock.tryAcquire(LOCK_KEY, lockTtl)) {
            log.debug("搜索索引同步跳过：未取得锁 {}", LOCK_KEY);
            return;
        }
        long startedAt = System.currentTimeMillis();
        try {
            indexGateway.ensureIndex();
            Optional<SearchSyncCursor> cursor = stateGateway.read();
            if (cursor.isEmpty() || indexGateway.documentCount() == 0) {
                Instant reindexStartedAt = Instant.now();
                SearchIndexApplicationService.FullReindexOutcome outcome =
                        indexService.fullReindex(new SearchSyncCursor(reindexStartedAt, 0L));
                stateGateway.write(outcome.cursor());
                log.info("搜索索引全量重建完成：{} 条，耗时 {} ms", outcome.indexed(),
                        System.currentTimeMillis() - startedAt);
            } else {
                SearchIndexApplicationService.SyncOutcome outcome = indexService.syncIncremental(cursor.get());
                stateGateway.write(outcome.cursor());
                log.info("搜索索引增量完成：写入 {}、删除 {}，耗时 {} ms",
                        outcome.upserted(), outcome.deleted(), System.currentTimeMillis() - startedAt);
            }
        } catch (RuntimeException e) {
            // 定时任务没有调用方：自己记带上下文的 ERROR，不推进游标，下一轮 fixedDelay 自动重试
            log.error("搜索索引同步失败：本轮游标未推进，下一轮重试", e);
        }
    }
}
