package com.campuslink.module.messaging.domain.model;

import lombok.Getter;

import java.time.Instant;

/**
 * 私信消息实体（F-SOC-003 / CR-135）：纯文本、只追加、不可编辑。
 *
 * <p>内容上限 1000 字由聚合与 web 校验注解两处同源约定（后者进契约、前者防绕过）；
 * 落库前 {@code strip()} 去首尾空白，换行等内部格式原样保留（前端以 {@code pre-wrap} 呈现）。
 */
@Getter
public class Message {

    /** 与 web 侧 {@code @Size(max = 1000)} 同源 */
    public static final int MAX_CONTENT_LENGTH = 1000;

    private final Long id;
    private final long conversationId;
    private final long senderId;
    private final long receiverId;
    private final String content;
    private final Instant createdAt;
    private final Instant readAt;

    private Message(Long id, long conversationId, long senderId, long receiverId,
                    String content, Instant createdAt, Instant readAt) {
        this.id = id;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.content = content;
        this.createdAt = createdAt;
        this.readAt = readAt;
    }

    /** 新消息：发送时间由应用层给定（与 {@code last_message_at} 同一时刻，避免两次取时抖动） */
    public static Message compose(long conversationId, long senderId, long receiverId,
                                  String content, Instant createdAt) {
        String normalized = content == null ? "" : content.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("私信内容不能为空");
        }
        if (normalized.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("私信内容不能超过 " + MAX_CONTENT_LENGTH + " 字");
        }
        return new Message(null, conversationId, senderId, receiverId, normalized, createdAt, null);
    }

    /** 仓储重建入口（infrastructure 适配器调用） */
    public static Message rehydrate(Long id, long conversationId, long senderId, long receiverId,
                                    String content, Instant createdAt, Instant readAt) {
        return new Message(id, conversationId, senderId, receiverId, content, createdAt, readAt);
    }
}
