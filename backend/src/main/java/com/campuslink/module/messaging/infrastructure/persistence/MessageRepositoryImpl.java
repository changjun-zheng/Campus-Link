package com.campuslink.module.messaging.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campuslink.module.messaging.domain.gateway.MessageRepository;
import com.campuslink.module.messaging.domain.model.Message;
import com.campuslink.module.messaging.infrastructure.persistence.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/** 适配器：MessageRepository 端口的 MyBatis-Plus 实现（CR-135） */
@Repository
@RequiredArgsConstructor
public class MessageRepositoryImpl implements MessageRepository {

    private final MessageMapper messageMapper;

    @Override
    public Message save(Message message) {
        MessageDO row = new MessageDO();
        row.setConversationId(message.getConversationId());
        row.setSenderId(message.getSenderId());
        row.setReceiverId(message.getReceiverId());
        row.setContent(message.getContent());
        row.setCreatedAt(message.getCreatedAt());
        messageMapper.insert(row);
        return Message.rehydrate(row.getId(), message.getConversationId(), message.getSenderId(),
                message.getReceiverId(), message.getContent(), message.getCreatedAt(), null);
    }

    @Override
    public List<Message> pageByConversation(long conversationId, int offset, int limit) {
        return messageMapper.pageByConversation(conversationId, offset, limit).stream()
                .map(MessageRepositoryImpl::toDomain)
                .toList();
    }

    @Override
    public long countByConversation(long conversationId) {
        return messageMapper.countByConversation(conversationId);
    }

    @Override
    public int markRead(long conversationId, long receiverId, Instant readAt) {
        // read_at IS NULL 前置条件让 UPDATE 只命中未读行：affected rows 即"本次新标记的条数"，重复调用返回 0
        return messageMapper.update(null, new LambdaUpdateWrapper<MessageDO>()
                .set(MessageDO::getReadAt, readAt)
                .eq(MessageDO::getConversationId, conversationId)
                .eq(MessageDO::getReceiverId, receiverId)
                .isNull(MessageDO::getReadAt));
    }

    @Override
    public long countUnread(long userId) {
        return messageMapper.countUnread(userId);
    }

    private static Message toDomain(MessageDO row) {
        return Message.rehydrate(row.getId(), row.getConversationId(), row.getSenderId(), row.getReceiverId(),
                row.getContent(), row.getCreatedAt(), row.getReadAt());
    }
}
