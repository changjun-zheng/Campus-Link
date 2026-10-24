package com.campuslink.module.messaging.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campuslink.module.messaging.domain.gateway.ConversationRepository;
import com.campuslink.module.messaging.domain.model.Conversation;
import com.campuslink.module.messaging.domain.model.ConversationSummary;
import com.campuslink.module.messaging.infrastructure.persistence.mapper.ConversationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 适配器：ConversationRepository 端口的 MyBatis-Plus 实现（CR-135） */
@Repository
@RequiredArgsConstructor
public class ConversationRepositoryImpl implements ConversationRepository {

    private final ConversationMapper conversationMapper;

    @Override
    public Optional<Conversation> findByPair(long lowId, long highId) {
        ConversationDO row = conversationMapper.selectOne(new LambdaQueryWrapper<ConversationDO>()
                .eq(ConversationDO::getPairLowId, lowId)
                .eq(ConversationDO::getPairHighId, highId));
        return Optional.ofNullable(row).map(ConversationRepositoryImpl::toDomain);
    }

    @Override
    public Conversation create(long lowId, long highId) {
        ConversationDO row = new ConversationDO();
        row.setPairLowId(lowId);
        row.setPairHighId(highId);
        try {
            conversationMapper.insert(row);
            return Conversation.rehydrate(row.getId(), lowId, highId, null, null, null);
        } catch (DuplicateKeyException e) {
            // 并发首条消息：另一事务已建同一对会话——唯一键是幂等来源，回读既有行继续发消息
            return findByPair(lowId, highId).orElseThrow(() -> e);
        }
    }

    @Override
    public void updateLastMessage(long conversationId, long messageId, Instant lastMessageAt) {
        conversationMapper.update(null, new LambdaUpdateWrapper<ConversationDO>()
                .set(ConversationDO::getLastMessageId, messageId)
                .set(ConversationDO::getLastMessageAt, lastMessageAt)
                .eq(ConversationDO::getId, conversationId));
    }

    @Override
    public List<ConversationSummary> pageOfUser(long userId, int offset, int limit) {
        return conversationMapper.pageOfUser(userId, offset, limit).stream()
                .map(ConversationRepositoryImpl::toSummary)
                .toList();
    }

    @Override
    public long countOfUser(long userId) {
        return conversationMapper.countOfUser(userId);
    }

    private static Conversation toDomain(ConversationDO row) {
        return Conversation.rehydrate(row.getId(), row.getPairLowId(), row.getPairHighId(),
                row.getLastMessageId(), row.getLastMessageAt(), row.getCreatedAt());
    }

    private static ConversationSummary toSummary(ConversationSummaryDO row) {
        return new ConversationSummary(row.getConversationId(), row.getPeerId(),
                row.getPeerNickname(), row.getPeerAvatarUrl(), row.getLastMessagePreview(),
                row.getLastMessageAt(), row.getUnreadCount() == null ? 0L : row.getUnreadCount());
    }
}
