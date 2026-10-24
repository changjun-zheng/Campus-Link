package com.campuslink.module.messaging.domain.model;

import lombok.Getter;

import java.time.Instant;

/**
 * 私信会话聚合（F-SOC-003 / CR-135）：一人对一人只有一个会话。
 *
 * <p>双方 id 以 {@code (min, max)} 归一存储，{@code uk_conversations_pair} 唯一键即
 * "重复建会话天然幂等"；归一只在 {@link #pairOf} 做一次，应用层与仓储都消费归一后的值，
 * 避免"同一对人两个会话"这类读侧永远发现不了的脏数据。
 */
@Getter
public class Conversation {

    private final Long id;
    private final long pairLowId;
    private final long pairHighId;
    private final Long lastMessageId;
    private final Instant lastMessageAt;
    private final Instant createdAt;

    private Conversation(Long id, long pairLowId, long pairHighId,
                         Long lastMessageId, Instant lastMessageAt, Instant createdAt) {
        this.id = id;
        this.pairLowId = pairLowId;
        this.pairHighId = pairHighId;
        this.lastMessageId = lastMessageId;
        this.lastMessageAt = lastMessageAt;
        this.createdAt = createdAt;
    }

    /** 新会话：尚未有消息，摘要列由首发消息回写 */
    public static Conversation start(long userA, long userB) {
        Pair pair = pairOf(userA, userB);
        return new Conversation(null, pair.lowId(), pair.highId(), null, null, null);
    }

    /** 仓储重建入口（infrastructure 适配器调用） */
    public static Conversation rehydrate(Long id, long pairLowId, long pairHighId,
                                         Long lastMessageId, Instant lastMessageAt, Instant createdAt) {
        return new Conversation(id, pairLowId, pairHighId, lastMessageId, lastMessageAt, createdAt);
    }

    /** 归一化双方 id：自己与自己不存在会话（发送用例在更早的一层已拦为 2015） */
    public static Pair pairOf(long userA, long userB) {
        if (userA == userB) {
            throw new IllegalArgumentException("自己与自己不存在私信会话");
        }
        return userA < userB ? new Pair(userA, userB) : new Pair(userB, userA);
    }

    /** 归一后的双方 id 对 */
    public record Pair(long lowId, long highId) {
    }
}
