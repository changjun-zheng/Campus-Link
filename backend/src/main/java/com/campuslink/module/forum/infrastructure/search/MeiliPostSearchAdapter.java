package com.campuslink.module.forum.infrastructure.search;

import com.campuslink.config.AppProperties;
import com.campuslink.module.forum.domain.gateway.PostSearchGateway;
import com.campuslink.module.forum.domain.gateway.PostSearchIndexGateway;
import com.campuslink.module.forum.domain.model.PostSearchDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MeiliSearch 适配器（CR-181 / ADR-004）：读端口 + 索引写端口的 HTTP 实现。
 *
 * <p>不引入 Meili SDK——接口面很小（建索引 / 设置 / 批量文档 / 删除 / 搜索 / 任务轮询），
 * 用 Spring {@link RestClient} 直连即可，少一个版本耦合。所有索引写操作在收到 task {@code succeeded}
 * 后才返回；失败抛 {@link SearchEngineUnavailableException}，由应用层回落 MySQL。
 *
 * <p>索引字段：{@code id / title / content / tags / board_id / author_id / is_anonymous / created_at /
 * status / is_deleted}；{@code created_at} 存 epoch 秒（数值过滤 + 作为相关度之后的次级排序）。
 * 入索引的只有可见帖子，{@code status / is_deleted} 过滤是纵深防御。
 */
@Component
@RequiredArgsConstructor
public class MeiliPostSearchAdapter implements PostSearchGateway, PostSearchIndexGateway {

    private static final Duration TASK_POLL = Duration.ofMillis(50);
    private static final int TASK_MAX_ATTEMPTS = 600;
    private static final int MAX_TOTAL_HITS = 10_000;

    private final RestClient meiliRestClient;
    private final AppProperties props;
    private final ObjectMapper objectMapper;

    /**
     * 本进程内已确认索引与设置就位：调度器每 10s 调一次 ensureIndex，若每次都 PATCH 全量设置，
     * 大索引会被反复触发设置任务；缓存后只在启动 / 首次同步时做一次。
     */
    private volatile boolean ensured;

    @Override
    public SearchHits search(String keyword, Long boardId, Integer days, Collection<Long> excludedAuthorIds,
                             Collection<Long> excludedPostIds, int page, int size) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("q", keyword);
        body.put("filter", buildFilter(boardId, days, excludedAuthorIds, excludedPostIds));
        body.put("limit", size);
        body.put("offset", (page - 1) * size);
        body.put("attributesToRetrieve", List.of("id"));
        Map<String, Object> response = postJson(indexUri("/search"), body);
        List<Long> ids = new ArrayList<>();
        if (response.get("hits") instanceof List<?> hits) {
            for (Object hit : hits) {
                if (hit instanceof Map<?, ?> map && map.get("id") instanceof Number number) {
                    ids.add(number.longValue());
                }
            }
        }
        long total = response.get("estimatedTotalHits") instanceof Number number ? number.longValue() : ids.size();
        return new SearchHits(ids, total);
    }

    @Override
    public void ensureIndex() {
        if (ensured) {
            return;
        }
        int status;
        try {
            status = meiliRestClient.get().uri(indexUri(""))
                    .exchange((request, response) -> response.getStatusCode().value());
        } catch (RestClientException e) {
            throw unavailable("查询索引", e);
        }
        if (status == 404) {
            waitTask(taskUid(postJson("/indexes", Map.of("uid", indexUid(), "primaryKey", "id"))));
        } else if (status >= 400) {
            throw new SearchEngineUnavailableException("Meili 索引查询失败 HTTP " + status);
        }
        waitTask(taskUid(patchJson(indexUri("/settings"), settings())));
        ensured = true;
    }

    @Override
    public long documentCount() {
        try {
            return meiliRestClient.get().uri(indexUri("/stats")).exchange((request, response) -> {
                if (response.getStatusCode().value() == 404) {
                    return 0L;
                }
                if (!response.getStatusCode().is2xxSuccessful()) {
                    throw new SearchEngineUnavailableException("Meili 索引统计失败 HTTP "
                            + response.getStatusCode().value());
                }
                Map<?, ?> stats = objectMapper.readValue(response.getBody(), Map.class);
                return stats.get("numberOfDocuments") instanceof Number number ? number.longValue() : 0L;
            });
        } catch (RestClientException e) {
            throw unavailable("查询索引统计", e);
        } catch (SearchEngineUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw unavailable("解析索引统计", e);
        }
    }

    @Override
    public void clear() {
        waitTask(taskUid(deleteJson(indexUri("/documents"))));
    }

    @Override
    public void upsert(Collection<PostSearchDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        List<Map<String, Object>> payload = documents.stream()
                .map(MeiliPostSearchAdapter::toPayload)
                .toList();
        waitTask(taskUid(postJson(indexUri("/documents?primaryKey=id"), payload)));
    }

    @Override
    public void deleteByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        waitTask(taskUid(postJson(indexUri("/documents/delete-batch"), ids)));
    }

    private Map<String, Object> settings() {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("searchableAttributes", List.of("title", "content", "tags"));
        settings.put("filterableAttributes", List.of(
                "id", "board_id", "author_id", "is_anonymous", "created_at", "status", "is_deleted"));
        settings.put("sortableAttributes", List.of("created_at"));
        // 相关度规则之后以 created_at 倒序兜底：等价于原「相关度 + 时间」口径，但标题 2× 权重改为属性优先级
        settings.put("rankingRules", List.of(
                "words", "typo", "proximity", "attribute", "sort", "exactness", "created_at:desc"));
        settings.put("pagination", Map.of("maxTotalHits", MAX_TOTAL_HITS));
        // 关闭拼写容错：真机对照发现 2 字中文查询会被容错扩到「分页→分数」这类近词，
        // 与 MySQL ngram 的精确子串语义不符；论坛搜索要可预期，宁可零结果也不给错结果。
        settings.put("typoTolerance", Map.of("enabled", false));
        return settings;
    }

    private String buildFilter(Long boardId, Integer days, Collection<Long> excludedAuthorIds,
                               Collection<Long> excludedPostIds) {
        List<String> filters = new ArrayList<>();
        filters.add("status = 'PUBLISHED'");
        filters.add("is_deleted = false");
        if (boardId != null) {
            filters.add("board_id = " + boardId);
        }
        if (days != null) {
            filters.add("created_at >= " + Instant.now().minus(days, ChronoUnit.DAYS).getEpochSecond());
        }
        if (excludedAuthorIds != null && !excludedAuthorIds.isEmpty()) {
            // 匿名帖例外：与 SQL 侧 (is_anonymous = 1 OR author_id NOT IN ...) 一致，避免按作者反推身份
            filters.add("(is_anonymous = true OR author_id NOT IN [" + joinIds(excludedAuthorIds) + "])");
        }
        if (excludedPostIds != null && !excludedPostIds.isEmpty()) {
            filters.add("id NOT IN [" + joinIds(excludedPostIds) + "]");
        }
        return String.join(" AND ", filters);
    }

    private static String joinIds(Collection<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private static Map<String, Object> toPayload(PostSearchDocument document) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", document.id());
        payload.put("title", document.title() == null ? "" : document.title());
        payload.put("content", document.content() == null ? "" : document.content());
        payload.put("tags", document.tags());
        payload.put("board_id", document.boardId());
        payload.put("author_id", document.authorId());
        payload.put("is_anonymous", document.anonymous());
        payload.put("created_at", document.createdAt() == null ? 0L : document.createdAt().getEpochSecond());
        payload.put("status", "PUBLISHED");
        payload.put("is_deleted", false);
        return payload;
    }

    private void waitTask(Long taskUid) {
        if (taskUid == null) {
            return;
        }
        for (int attempt = 0; attempt < TASK_MAX_ATTEMPTS; attempt++) {
            Map<String, Object> task = getJson("/tasks/" + taskUid);
            String status = String.valueOf(task.get("status"));
            if ("succeeded".equals(status)) {
                return;
            }
            if ("failed".equals(status) || "canceled".equals(status)) {
                throw new SearchEngineUnavailableException("Meili 索引任务 " + taskUid + " " + status
                        + "：" + task.get("error"));
            }
            sleep();
        }
        throw new SearchEngineUnavailableException("Meili 索引任务超时：" + taskUid);
    }

    private static void sleep() {
        try {
            Thread.sleep(TASK_POLL.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SearchEngineUnavailableException("Meili 索引任务等待被中断", e);
        }
    }

    private static Long taskUid(Map<String, Object> response) {
        Object uid = response.get("taskUid");
        if (uid == null) {
            uid = response.get("uid");
        }
        return uid instanceof Number number ? number.longValue() : null;
    }

    private Map<String, Object> getJson(String uri) {
        try {
            return meiliRestClient.get().uri(uri).retrieve().body(Map.class);
        } catch (RestClientException e) {
            throw unavailable("GET " + uri, e);
        }
    }

    private Map<String, Object> postJson(String uri, Object body) {
        try {
            return meiliRestClient.post().uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json(body))
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException e) {
            throw unavailable("POST " + uri, e);
        }
    }

    private Map<String, Object> patchJson(String uri, Object body) {
        try {
            return meiliRestClient.patch().uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json(body))
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException e) {
            throw unavailable("PATCH " + uri, e);
        }
    }

    private Map<String, Object> deleteJson(String uri) {
        try {
            return meiliRestClient.delete().uri(uri).retrieve().body(Map.class);
        } catch (RestClientException e) {
            throw unavailable("DELETE " + uri, e);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new SearchEngineUnavailableException("Meili 请求序列化失败", e);
        }
    }

    private SearchEngineUnavailableException unavailable(String action, Exception cause) {
        return new SearchEngineUnavailableException("Meili " + action + " 失败：" + cause.getMessage(), cause);
    }

    private String indexUid() {
        return props.getSearch().getMeili().getIndex();
    }

    private String indexUri(String suffix) {
        return "/indexes/" + indexUid() + suffix;
    }
}
