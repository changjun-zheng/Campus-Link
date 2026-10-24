package com.campuslink.module.messaging.infrastructure.persistence;

import com.campuslink.module.messaging.domain.model.Conversation;
import com.campuslink.module.messaging.domain.model.ConversationSummary;
import com.campuslink.module.messaging.infrastructure.persistence.mapper.ConversationMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationRepositoryImplTest {

    @Mock
    private ConversationMapper conversationMapper;
    @InjectMocks
    private ConversationRepositoryImpl repository;

    /** 纯单测没有 SqlSession，lambda 条件的列名解析依赖 MP 的 TableInfo 缓存，先手工预热 */
    @BeforeAll
    static void initTableInfoCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(ConversationMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, ConversationDO.class);
    }

    @Test
    @DisplayName("按归一 pair 查会话：行字段完整映射到聚合")
    void findByPairMapsRow() {
        ConversationDO row = new ConversationDO();
        row.setId(55L);
        row.setPairLowId(1L);
        row.setPairHighId(8L);
        row.setLastMessageId(9L);
        Instant at = Instant.parse("2026-10-24T12:00:00Z");
        row.setLastMessageAt(at);
        row.setCreatedAt(at.minusSeconds(60));
        when(conversationMapper.selectOne(any())).thenReturn(row);

        Optional<Conversation> found = repository.findByPair(1L, 8L);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(55L);
        assertThat(found.get().getPairLowId()).isEqualTo(1L);
        assertThat(found.get().getPairHighId()).isEqualTo(8L);
        assertThat(found.get().getLastMessageId()).isEqualTo(9L);
        assertThat(found.get().getLastMessageAt()).isEqualTo(at);
    }

    @Test
    @DisplayName("新建会话：插入后 id 回填，返回带 id 的聚合")
    void createInsertsAndReturnsGeneratedId() {
        when(conversationMapper.insert(any(ConversationDO.class))).thenAnswer(invocation -> {
            ConversationDO row = invocation.getArgument(0);
            row.setId(77L);
            return 1;
        });

        Conversation created = repository.create(1L, 8L);

        assertThat(created.getId()).isEqualTo(77L);
        assertThat(created.getPairLowId()).isEqualTo(1L);
        assertThat(created.getPairHighId()).isEqualTo(8L);
        assertThat(created.getLastMessageId()).isNull();
    }

    @Test
    @DisplayName("并发首条消息撞唯一键 → 回读既有行（幂等，不向上抛异常）")
    void createOnDuplicateReadsExistingConversation() {
        when(conversationMapper.insert(any(ConversationDO.class)))
                .thenThrow(new DuplicateKeyException("uk_conversations_pair"));
        ConversationDO existing = new ConversationDO();
        existing.setId(88L);
        existing.setPairLowId(1L);
        existing.setPairHighId(8L);
        when(conversationMapper.selectOne(any())).thenReturn(existing);

        Conversation created = repository.create(1L, 8L);

        assertThat(created.getId()).isEqualTo(88L);
    }

    @Test
    @DisplayName("撞唯一键但回读不到 → 原样抛出（不吞掉真正的数据异常）")
    void createOnDuplicateWithoutExistingRethrows() {
        when(conversationMapper.insert(any(ConversationDO.class)))
                .thenThrow(new DuplicateKeyException("uk_conversations_pair"));
        when(conversationMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> repository.create(1L, 8L))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("回写最后消息：定向更新 last_message_id / last_message_at")
    void updateLastMessageUpdatesTwoColumns() {
        Instant at = Instant.parse("2026-10-24T12:00:00Z");

        repository.updateLastMessage(55L, 101L, at);

        verify(conversationMapper).update(isNull(), any());
    }

    @Test
    @DisplayName("会话分页：联表行映射为领域窄载体（unreadCount 为空时回落 0）")
    void pageOfUserMapsRows() {
        Instant at = Instant.parse("2026-10-24T12:00:00Z");
        ConversationSummaryDO row = new ConversationSummaryDO();
        row.setConversationId(55L);
        row.setPeerId(8L);
        row.setPeerNickname("模拟学生08");
        row.setPeerAvatarUrl("https://a.png");
        row.setLastMessagePreview("最后一条");
        row.setLastMessageAt(at);
        row.setUnreadCount(null);
        when(conversationMapper.pageOfUser(1L, 20, 10)).thenReturn(List.of(row));

        List<ConversationSummary> items = repository.pageOfUser(1L, 20, 10);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).conversationId()).isEqualTo(55L);
        assertThat(items.get(0).peerId()).isEqualTo(8L);
        assertThat(items.get(0).peerNickname()).isEqualTo("模拟学生08");
        assertThat(items.get(0).peerAvatarUrl()).isEqualTo("https://a.png");
        assertThat(items.get(0).lastMessagePreview()).isEqualTo("最后一条");
        assertThat(items.get(0).unreadCount()).isZero();
    }

    @Test
    @DisplayName("会话总数：参数透传给 Mapper")
    void countOfUserDelegates() {
        when(conversationMapper.countOfUser(1L)).thenReturn(3L);

        assertThat(repository.countOfUser(1L)).isEqualTo(3L);
    }
}
