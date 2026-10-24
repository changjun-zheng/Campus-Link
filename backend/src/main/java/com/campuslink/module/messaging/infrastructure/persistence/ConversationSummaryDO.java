package com.campuslink.module.messaging.infrastructure.persistence;

import lombok.Data;

import java.time.Instant;

/**
 * 会话列表联表查询行（CR-135）：{@code conversations} + {@code users} + 未读子查询的只读投影。
 * 列名经 {@code map-underscore-to-camel-case} 映射到同名属性，再由仓储实现转成领域窄载体。
 */
@Data
public class ConversationSummaryDO {

    private Long conversationId;
    private Long peerId;
    private String peerNickname;
    private String peerAvatarUrl;
    private String lastMessagePreview;
    private Instant lastMessageAt;
    private Long unreadCount;
}
