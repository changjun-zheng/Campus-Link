package com.campuslink.module.forum.infrastructure.search;

import com.campuslink.config.AppProperties;
import com.campuslink.module.forum.domain.gateway.PostSearchGateway.SearchHits;
import com.campuslink.module.forum.domain.model.PostSearchDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MeiliPostSearchAdapterTest {

    private MockRestServiceServer server;
    private MeiliPostSearchAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://meili.test");
        server = MockRestServiceServer.bindTo(builder).build();
        AppProperties props = new AppProperties();
        props.getSearch().getMeili().setIndex("posts_v1");
        adapter = new MeiliPostSearchAdapter(builder.build(), props, new ObjectMapper());
    }

    @Test
    @DisplayName("搜索：请求带版块/天数/拉黑/举报过滤，按返回顺序解析 id 与估算 total")
    void searchBuildsFilterAndParsesHits() {
        server.expect(requestTo("http://meili.test/indexes/posts_v1/search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(allOf(
                        containsString("\"q\":\"Redis\""),
                        containsString("board_id = 1"),
                        containsString("created_at >= "),
                        containsString("(is_anonymous = true OR author_id NOT IN [7])"),
                        containsString("id NOT IN [9]"),
                        containsString("\"offset\":20"))))
                .andRespond(withSuccess("{\"hits\":[{\"id\":3},{\"id\":1}],\"estimatedTotalHits\":42}",
                        MediaType.APPLICATION_JSON));

        SearchHits hits = adapter.search("Redis", 1L, 30, Set.of(7L), Set.of(9L), 2, 20);

        assertThat(hits.ids()).containsExactly(3L, 1L);
        assertThat(hits.estimatedTotal()).isEqualTo(42);
        server.verify();
    }

    @Test
    @DisplayName("ensureIndex：索引不存在则创建，随后写入 searchable/filterable/ranking 设置")
    void ensureIndexCreatesMissingIndexAndPatchesSettings() {
        server.expect(requestTo("http://meili.test/indexes/posts_v1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo("http://meili.test/indexes"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"taskUid\":1}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://meili.test/tasks/1"))
                .andRespond(withSuccess("{\"status\":\"succeeded\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://meili.test/indexes/posts_v1/settings"))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withSuccess("{\"taskUid\":2}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://meili.test/tasks/2"))
                .andRespond(withSuccess("{\"status\":\"succeeded\"}", MediaType.APPLICATION_JSON));

        adapter.ensureIndex();

        server.verify();
    }

    @Test
    @DisplayName("upsert / delete-by-id 都等待索引任务成功后才返回")
    void upsertAndDeleteWaitForTask() {
        server.expect(requestTo("http://meili.test/indexes/posts_v1/documents?primaryKey=id"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"id\":7")))
                .andRespond(withSuccess("{\"taskUid\":3}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://meili.test/tasks/3"))
                .andRespond(withSuccess("{\"status\":\"succeeded\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://meili.test/indexes/posts_v1/documents/delete-batch"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("8")))
                .andRespond(withSuccess("{\"taskUid\":4}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://meili.test/tasks/4"))
                .andRespond(withSuccess("{\"status\":\"succeeded\"}", MediaType.APPLICATION_JSON));

        adapter.upsert(List.of(document(7L)));
        adapter.deleteByIds(List.of(8L, 9L));

        server.verify();
    }

    @Test
    @DisplayName("索引缺失时 documentCount 返回 0（触发全量重建），不抛异常")
    void documentCountReturnsZeroForMissingIndex() {
        server.expect(requestTo("http://meili.test/indexes/posts_v1/stats"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(adapter.documentCount()).isZero();
        server.verify();
    }

    private static PostSearchDocument document(long id) {
        Instant now = Instant.parse("2026-11-01T00:00:00Z");
        return new PostSearchDocument(id, "标题", "正文", List.of("Redis"), 1L, 100L, false, now, now, true);
    }
}
