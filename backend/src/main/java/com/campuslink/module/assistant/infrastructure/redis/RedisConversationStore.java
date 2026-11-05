package com.campuslink.module.assistant.infrastructure.redis;

import com.campuslink.common.redis.RedisKeys;
import com.campuslink.module.assistant.domain.gateway.ConversationStore;
import com.campuslink.module.assistant.domain.model.AssistantConversation;
import com.campuslink.module.assistant.domain.model.AssistantMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.ObjectMapper;

/**
 * Redis 会话存储（CR-200）：只存 30 分钟，不落 MySQL。
 *
 * <p>用基础设施 DTO 而不是直接序列化 domain：domain 保持无 Jackson 注解，时间戳统一存 epoch 毫秒。
 * 读到损坏 JSON 时删键并返回空，按"会话已过期"处理，避免坏数据反复命中。
 */
@Component
public class RedisConversationStore implements ConversationStore {

    private static final Logger log = LoggerFactory.getLogger(RedisConversationStore.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisConversationStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<AssistantConversation> find(String conversationId) {
        String key = key(conversationId);
        String json = redis.opsForValue().get(key);
        if (json == null) {
            return Optional.empty();
        }
        try {
            StoredConversation stored = objectMapper.readValue(json, StoredConversation.class);
            return Optional.of(toDomain(stored));
        } catch (Exception e) {
            log.warn("assistant conversation unreadable, dropping key: {}", e.getClass().getSimpleName());
            redis.delete(key);
            return Optional.empty();
        }
    }

    @Override
    public void save(AssistantConversation conversation, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(fromDomain(conversation));
            redis.opsForValue().set(key(conversation.id()), json, ttl);
        } catch (Exception e) {
            throw new IllegalStateException("保存 AI 会话失败", e);
        }
    }

    @Override
    public void delete(String conversationId) {
        redis.delete(key(conversationId));
    }

    private String key(String conversationId) {
        return RedisKeys.of("assistant:session:" + conversationId);
    }

    private StoredConversation fromDomain(AssistantConversation conversation) {
        List<StoredMessage> messages = conversation.messages().stream()
                .map(message -> new StoredMessage(message.role().name(), message.content()))
                .toList();
        return new StoredConversation(conversation.id(), conversation.userId(), conversation.postId(),
                conversation.postTitle(), conversation.boardName(), conversation.selectedText(), messages,
                conversation.createdAt().toEpochMilli(), conversation.updatedAt().toEpochMilli());
    }

    private AssistantConversation toDomain(StoredConversation stored) {
        List<AssistantMessage> messages = stored.messages() == null ? List.of()
                : stored.messages().stream()
                        .map(message -> new AssistantMessage(AssistantMessage.Role.valueOf(message.role()),
                                message.content()))
                        .toList();
        return AssistantConversation.restore(stored.id(), stored.userId(), stored.postId(),
                stored.postTitle(), stored.boardName(), stored.selectedText(), messages,
                Instant.ofEpochMilli(stored.createdAt()), Instant.ofEpochMilli(stored.updatedAt()));
    }

    private record StoredConversation(String id, long userId, long postId, String postTitle, String boardName,
            String selectedText, List<StoredMessage> messages, long createdAt, long updatedAt) {
    }

    private record StoredMessage(String role, String content) {
    }
}
