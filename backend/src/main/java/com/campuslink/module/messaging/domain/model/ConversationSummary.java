package com.campuslink.module.messaging.domain.model;

import java.time.Instant;

/**
 * 会话列表窄读载体（F-SOC-003 / CR-135）：会话 + 对方资料 + 摘要 + 该会话未读数。
 *
 * <p>为什么用独立载体而不是 {@link Conversation} 聚合：对方昵称 / 头像与未读计数不属于会话本身，
 * 却必须在**同一条 SQL** 里出（{@code total} 与页内容同 WHERE、列表不出现 N+1）；
 * 聚合继续只承载关系事实。
 */
public record ConversationSummary(long conversationId,
                                  long peerId,
                                  String peerNickname,
                                  String peerAvatarUrl,
                                  String lastMessagePreview,
                                  Instant lastMessageAt,
                                  long unreadCount) {
}
