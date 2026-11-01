package com.campuslink.module.forum.application;

import com.campuslink.config.AppProperties;
import com.campuslink.module.forum.domain.gateway.PostRepository;
import com.campuslink.module.forum.domain.gateway.PostSearchIndexGateway;
import com.campuslink.module.forum.domain.model.PostSearchDocument;
import com.campuslink.module.forum.domain.model.SearchSyncCursor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchIndexApplicationServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-01T00:00:00Z");

    @Mock
    private PostRepository postRepository;
    @Mock
    private PostSearchIndexGateway indexGateway;

    private SearchIndexApplicationService service;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        props.getSearch().setBatchSize(2);
        service = new SearchIndexApplicationService(postRepository, indexGateway, props);
    }

    @Test
    @DisplayName("增量同步：可见帖子 upsert、删除/下架按 id 删除，游标推进到本批末尾")
    void incrementalUpsertsVisibleAndDeletesHidden() {
        PostSearchDocument visible = doc(1L, true, T0.plusSeconds(1));
        PostSearchDocument hidden = doc(2L, false, T0.plusSeconds(2));
        when(postRepository.findSearchDocumentsChangedSince(T0, 0L, 2)).thenReturn(List.of(visible, hidden));
        when(postRepository.findSearchDocumentsChangedSince(T0.plusSeconds(2), 2L, 2)).thenReturn(List.of());

        SearchIndexApplicationService.SyncOutcome outcome =
                service.syncIncremental(new SearchSyncCursor(T0, 0L));

        assertThat(outcome.upserted()).isEqualTo(1);
        assertThat(outcome.deleted()).isEqualTo(1);
        assertThat(outcome.cursor()).isEqualTo(new SearchSyncCursor(T0.plusSeconds(2), 2L));
        verify(indexGateway).upsert(List.of(visible));
        verify(indexGateway).deleteByIds(List.of(2L));
    }

    @Test
    @DisplayName("全量重建：先清空，再按 id 游标分批写入；返回调用方给的游标起点")
    void fullReindexClearsThenBatchesById() {
        PostSearchDocument first = doc(1L, true, T0);
        PostSearchDocument second = doc(2L, true, T0);
        PostSearchDocument third = doc(3L, true, T0);
        when(postRepository.findSearchDocumentsAfterId(0L, 2)).thenReturn(List.of(first, second));
        when(postRepository.findSearchDocumentsAfterId(2L, 2)).thenReturn(List.of(third));
        when(postRepository.findSearchDocumentsAfterId(3L, 2)).thenReturn(List.of());
        SearchSyncCursor cursorAfter = new SearchSyncCursor(T0.plusSeconds(30), 0L);

        SearchIndexApplicationService.FullReindexOutcome outcome = service.fullReindex(cursorAfter);

        assertThat(outcome.indexed()).isEqualTo(3);
        assertThat(outcome.cursor()).isEqualTo(cursorAfter);
        InOrder order = inOrder(indexGateway);
        order.verify(indexGateway).clear();
        order.verify(indexGateway).upsert(List.of(first, second));
        order.verify(indexGateway).upsert(List.of(third));
    }

    private static PostSearchDocument doc(long id, boolean visible, Instant updatedAt) {
        return new PostSearchDocument(id, "标题" + id, "正文" + id, List.of("Redis"), 1L, 100L, false,
                T0, updatedAt, visible);
    }
}
