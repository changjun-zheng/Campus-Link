package com.campuslink.module.forum.infrastructure.redis;

import com.campuslink.common.redis.RedisKeys;
import com.campuslink.module.forum.domain.gateway.SearchSyncStateGateway;
import com.campuslink.module.forum.domain.model.SearchSyncCursor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Optional;

/**
 * 索引同步游标的 Redis 适配器（CR-181）：值形如 {@code <epochSecond>:<id>}，键带 {@code campuslink:} 前缀。
 *
 * <p>键丢失 / 值损坏一律返回空，由调度器改走全量重建——游标是加速器，不是正确性依赖。
 */
@Component
@RequiredArgsConstructor
public class RedisSearchSyncStateAdapter implements SearchSyncStateGateway {

    private static final String LOGICAL_KEY = "search:sync:cursor";

    private final StringRedisTemplate redis;

    @Override
    public Optional<SearchSyncCursor> read() {
        String raw = redis.opsForValue().get(RedisKeys.of(LOGICAL_KEY));
        if (!StringUtils.hasText(raw)) {
            return Optional.empty();
        }
        String[] parts = raw.split(":");
        if (parts.length != 2) {
            return Optional.empty();
        }
        try {
            return Optional.of(new SearchSyncCursor(
                    Instant.ofEpochSecond(Long.parseLong(parts[0])), Long.parseLong(parts[1])));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    @Override
    public void write(SearchSyncCursor cursor) {
        redis.opsForValue().set(RedisKeys.of(LOGICAL_KEY),
                cursor.updatedAt().getEpochSecond() + ":" + cursor.id());
    }

    @Override
    public void clear() {
        redis.delete(RedisKeys.of(LOGICAL_KEY));
    }
}
