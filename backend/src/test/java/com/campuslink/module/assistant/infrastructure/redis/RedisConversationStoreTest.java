package com.campuslink.module.assistant.infrastructure.redis;

import com.campuslink.common.redis.RedisKeys;
import com.campuslink.module.assistant.domain.model.AssistantConversation;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisConversationStoreTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private RedisConversationStore store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        store = new RedisConversationStore(redis, new ObjectMapper());
    }

    @Test
    @DisplayName("保存：键带 campuslink 前缀，值为可反序列化的 JSON，TTL 透传")
    void savesWithPrefixAndTtl() {
        AssistantConversation conversation = AssistantConversation.start(7L, 42L, "标题", "版块", "选中")
                .withUserMessage("问");

        store.save(conversation, Duration.ofMinutes(30));

        verify(valueOps).set(eq(RedisKeys.of("assistant:session:" + conversation.id())),
                any(String.class), eq(Duration.ofMinutes(30)));
    }

    @Test
    @DisplayName("读取：JSON 还原为领域对象")
    void findsAndRestores() {
        String json = """
                {"id":"c1","userId":7,"postId":42,"postTitle":"标题","boardName":"版块","selectedText":"选中",
                 "messages":[{"role":"USER","content":"问"},{"role":"ASSISTANT","content":"答"}],
                 "createdAt":1000,"updatedAt":2000}
                """;
        when(valueOps.get(RedisKeys.of("assistant:session:c1"))).thenReturn(json);

        Optional<AssistantConversation> found = store.find("c1");

        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo("c1");
        assertThat(found.get().userId()).isEqualTo(7L);
        assertThat(found.get().messages()).hasSize(2);
        assertThat(found.get().messages().get(1).content()).isEqualTo("答");
        assertThat(found.get().createdAt().toEpochMilli()).isEqualTo(1000);
    }

    @Test
    @DisplayName("读取：键不存在返回空")
    void missingReturnsEmpty() {
        when(valueOps.get(RedisKeys.of("assistant:session:none"))).thenReturn(null);

        assertThat(store.find("none")).isEmpty();
    }

    @Test
    @DisplayName("读取：损坏 JSON 删键并按过期处理")
    void corruptJsonIsDropped() {
        String key = RedisKeys.of("assistant:session:bad");
        when(valueOps.get(key)).thenReturn("{not-json");

        assertThat(store.find("bad")).isEmpty();
        verify(redis).delete(key);
    }

    @Test
    @DisplayName("删除：透传 Redis 键")
    void deletes() {
        store.delete("c1");

        verify(redis).delete(RedisKeys.of("assistant:session:c1"));
    }
}
