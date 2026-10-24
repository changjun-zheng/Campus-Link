package com.campuslink.module.messaging.domain.gateway;

import com.campuslink.module.messaging.domain.model.Conversation;
import com.campuslink.module.messaging.domain.model.ConversationSummary;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 出站端口：私信会话（F-SOC-003 / CR-135）。双方 id 由调用方归一后传入（{@link Conversation#pairOf}），
 * 实现侧只按 {@code (pair_low_id, pair_high_id)} 精确读写。
 */
public interface ConversationRepository {

    Optional<Conversation> findByPair(long lowId, long highId);

    /**
     * 新建会话；并发首条消息撞唯一键时回读既有行（幂等，不向上抛 DuplicateKeyException）。
     * 调用方须先把双方 id 归一为 {@code lowId < highId}。
     */
    Conversation create(long lowId, long highId);

    /** 回写最后一条消息摘要（发送用例在同一事务内调用，会话列表排序与预览只读这两列） */
    void updateLastMessage(long conversationId, long messageId, Instant lastMessageAt);

    /**
     * 我的会话分页：按最后一条消息时间倒序。条目与 {@code countOfUser} **同 WHERE**，
     * 因此 {@code total} 与页内容同源；不含任何消息的会话不出现（本轮会话由首发消息创建）。
     */
    List<ConversationSummary> pageOfUser(long userId, int offset, int limit);

    long countOfUser(long userId);
}
