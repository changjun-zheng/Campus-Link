package com.campuslink.module.messaging.web.vo;

import com.campuslink.module.messaging.application.cmd.MessagingResults.MessageItem;

import java.time.Instant;

/**
 * 消息条目（F-SOC-003 / CR-135）：纯文本 {@code content}，前端以 `white-space: pre-wrap` 呈现；
 * {@code readAt} 为 null 即接收人尚未读取（`non_null` 下该字段整个不出现）。
 */
public record MessageVo(long id,
                        long senderId,
                        long receiverId,
                        String content,
                        Instant createdAt,
                        Instant readAt) {

    public static MessageVo from(MessageItem item) {
        return new MessageVo(item.id(), item.senderId(), item.receiverId(),
                item.content(), item.createdAt(), item.readAt());
    }
}
