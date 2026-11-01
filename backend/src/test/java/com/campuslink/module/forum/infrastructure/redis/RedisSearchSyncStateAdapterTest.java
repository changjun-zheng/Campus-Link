package com.campuslink.module.forum.infrastructure.redis;

import com.campuslink.module.forum.domain.model.SearchSyncCursor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisSearchSyncStateAdapterTest {

    private static final String KEY = "campuslink:search:sync:cursor";

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private RedisSearchSyncStateAdapter adapter;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        adapter = new RedisSearchSyncStateAdapter(redis);
    }

    @Test
    @DisplayName("游标缺失或损坏返回空：由调度器改走全量重建，而不是卡在旧位置")
    void readReturnsEmptyWhenMissingOrMalformed() {
        when(valueOps.get(KEY)).thenReturn(null, "not-a-cursor");

        assertThat(adapter.read()).isEmpty();
        assertThat(adapter.read()).isEmpty();
    }

    @Test
    @DisplayName("写游标使用 campuslink 前缀与 epochSecond:id 格式")
    void writeUsesNamespacedKeyAndEpochSeconds() {
        adapter.write(new SearchSyncCursor(Instant.ofEpochSecond(1000), 7L));

        verify(valueOps).set(KEY, "1000:7");
    }

    @Test
    @DisplayName("读回同一个 (updatedAt,id) 游标")
    void readParsesCursor() {
        when(valueOps.get(KEY)).thenReturn("1000:7");

        assertThat(adapter.read())
                .contains(new SearchSyncCursor(Instant.ofEpochSecond(1000), 7L));
    }
}
