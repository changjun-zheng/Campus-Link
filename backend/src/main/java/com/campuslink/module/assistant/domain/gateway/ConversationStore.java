package com.campuslink.module.assistant.domain.gateway;

import com.campuslink.module.assistant.domain.model.AssistantConversation;

import java.time.Duration;
import java.util.Optional;

/**
 * 会话存储端口（CR-200）：现行实现是 Redis 30 分钟 TTL，不落 MySQL。
 * 读不到 / 已过期都返回空，由 application 统一映射为 7005。
 */
public interface ConversationStore {

    Optional<AssistantConversation> find(String conversationId);

    void save(AssistantConversation conversation, Duration ttl);

    void delete(String conversationId);
}
