package com.campuslink.module.messaging.application.cmd;

import java.time.Instant;
import java.util.List;

/**
 * 私信用例出参载体（F-SOC-003 / CR-135）：application 层不感知 web 的 VO 形状，
 * web 层再逐字段映射成契约 VO。
 */
public final class MessagingResults {

    private MessagingResults() {
    }

    /** 会话条目：对方资料 + 最后一条消息摘要 + 该会话未读数 */
    public record ConversationItem(long peerId,
                                   String peerNickname,
                                   String peerAvatarUrl,
                                   String lastMessagePreview,
                                   Instant lastMessageAt,
                                   long unreadCount) {
    }

    public record ConversationPage(List<ConversationItem> list, long total, int page, int size) {
    }

    /** 消息条目：{@code readAt} 为 null 即接收人尚未读取 */
    public record MessageItem(long id,
                              long senderId,
                              long receiverId,
                              String content,
                              Instant createdAt,
                              Instant readAt) {
    }

    public record MessagePage(List<MessageItem> list, long total, int page, int size) {
    }

    public record UnreadCount(long unreadCount) {
    }

    /** 标记已读结果：本次新标记的条数（重复调用为 0，不报错） */
    public record MarkReadResult(int updated) {
    }
}
