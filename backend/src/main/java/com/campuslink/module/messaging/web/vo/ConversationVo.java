package com.campuslink.module.messaging.web.vo;

import com.campuslink.module.messaging.application.cmd.MessagingResults.ConversationItem;

import java.time.Instant;

/**
 * 会话列表条目（F-SOC-003 / CR-135）：字段与前端 `api/messages.ts` 一一对应。
 * {@code peerAvatarUrl} / {@code lastMessagePreview} 可空，后端 `non_null` 序列化会把 null 字段整个删掉。
 */
public record ConversationVo(long peerId,
                             String peerNickname,
                             String peerAvatarUrl,
                             String lastMessagePreview,
                             Instant lastMessageAt,
                             long unreadCount) {

    public static ConversationVo from(ConversationItem item) {
        return new ConversationVo(item.peerId(), item.peerNickname(), item.peerAvatarUrl(),
                item.lastMessagePreview(), item.lastMessageAt(), item.unreadCount());
    }
}
