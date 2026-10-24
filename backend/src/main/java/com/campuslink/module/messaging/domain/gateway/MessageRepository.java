package com.campuslink.module.messaging.domain.gateway;

import com.campuslink.module.messaging.domain.model.Message;

import java.time.Instant;
import java.util.List;

/**
 * 出站端口：私信消息（F-SOC-003 / CR-135）。
 */
public interface MessageRepository {

    /** 追加一条消息并把生成的 id 回填到返回实体 */
    Message save(Message message);

    /** 会话内消息分页：最新在前（{@code created_at DESC, id DESC}） */
    List<Message> pageByConversation(long conversationId, int offset, int limit);

    long countByConversation(long conversationId);

    /** 把会话中"接收人是我且未读"的消息置为已读；返回本次新标记的条数（幂等） */
    int markRead(long conversationId, long receiverId, Instant readAt);

    /** 我的未读私信总数（顶栏角标） */
    long countUnread(long userId);
}
