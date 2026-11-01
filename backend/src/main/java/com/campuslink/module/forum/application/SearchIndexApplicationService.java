package com.campuslink.module.forum.application;

import com.campuslink.config.AppProperties;
import com.campuslink.module.forum.domain.gateway.PostRepository;
import com.campuslink.module.forum.domain.gateway.PostSearchIndexGateway;
import com.campuslink.module.forum.domain.model.PostSearchDocument;
import com.campuslink.module.forum.domain.model.SearchSyncCursor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 搜索索引同步用例（CR-181）。
 *
 * <p>增量按 {@code (updated_at, id)} 游标取变化行：可见 → upsert，删除 / 下架 → 按 id 删除；
 * 每批成功后才推进内存游标，整轮异常则不写 Redis 游标，下一轮重放同一批（upsert 幂等）。
 * 单轮最多 {@value #MAX_BATCHES_PER_RUN} 批，避免一次追平大量积压时长期占用调度线程。
 */
@Service
@RequiredArgsConstructor
public class SearchIndexApplicationService {

    private static final int MAX_BATCHES_PER_RUN = 20;

    private final PostRepository postRepository;
    private final PostSearchIndexGateway searchIndexGateway;
    private final AppProperties props;

    public SyncOutcome syncIncremental(SearchSyncCursor cursor) {
        int batchSize = props.getSearch().getBatchSize();
        SearchSyncCursor current = cursor;
        int upserted = 0;
        int deleted = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            List<PostSearchDocument> changed = postRepository.findSearchDocumentsChangedSince(
                    current.updatedAt(), current.id(), batchSize);
            if (changed.isEmpty()) {
                break;
            }
            List<PostSearchDocument> visible = changed.stream().filter(PostSearchDocument::visible).toList();
            List<Long> removed = changed.stream().filter(doc -> !doc.visible()).map(PostSearchDocument::id).toList();
            if (!visible.isEmpty()) {
                searchIndexGateway.upsert(visible);
            }
            if (!removed.isEmpty()) {
                searchIndexGateway.deleteByIds(removed);
            }
            upserted += visible.size();
            deleted += removed.size();
            PostSearchDocument last = changed.get(changed.size() - 1);
            current = new SearchSyncCursor(last.updatedAt(), last.id());
            if (changed.size() < batchSize) {
                break;
            }
        }
        return new SyncOutcome(current, upserted, deleted);
    }

    /**
     * 全量重建：先清空索引再按 id 游标分批写入可见帖子。
     *
     * <p>{@code cursorAfter} 由调度器传入**重建开始前**的时间点：重建期间被编辑 / 删除的帖子
     * 可能不在本次快照里，下一轮增量从该时间点重放，避免漏掉。
     */
    public FullReindexOutcome fullReindex(SearchSyncCursor cursorAfter) {
        int batchSize = props.getSearch().getBatchSize();
        searchIndexGateway.clear();
        long afterId = 0L;
        long indexed = 0L;
        while (true) {
            List<PostSearchDocument> batch = postRepository.findSearchDocumentsAfterId(afterId, batchSize);
            if (batch.isEmpty()) {
                break;
            }
            searchIndexGateway.upsert(batch);
            afterId = batch.get(batch.size() - 1).id();
            indexed += batch.size();
        }
        return new FullReindexOutcome(cursorAfter, indexed);
    }

    public record SyncOutcome(SearchSyncCursor cursor, int upserted, int deleted) {
    }

    public record FullReindexOutcome(SearchSyncCursor cursor, long indexed) {
    }
}
