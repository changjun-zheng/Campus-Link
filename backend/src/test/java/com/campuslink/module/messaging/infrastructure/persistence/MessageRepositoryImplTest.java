package com.campuslink.module.messaging.infrastructure.persistence;

import com.campuslink.module.messaging.domain.model.Message;
import com.campuslink.module.messaging.infrastructure.persistence.mapper.MessageMapper;
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

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageRepositoryImplTest {

    @Mock
    private MessageMapper messageMapper;
    @InjectMocks
    private MessageRepositoryImpl repository;

    /** 纯单测没有 SqlSession，lambda 条件的列名解析依赖 MP 的 TableInfo 缓存，先手工预热 */
    @BeforeAll
    static void initTableInfoCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(MessageMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, MessageDO.class);
    }

    @Test
    @DisplayName("落消息：字段完整写入，插入后 id 回填")
    void saveMapsFieldsAndReturnsId() {
        when(messageMapper.insert(any(MessageDO.class))).thenAnswer(invocation -> {
            MessageDO row = invocation.getArgument(0);
            row.setId(101L);
            return 1;
        });
        Instant now = Instant.parse("2026-10-24T12:00:00Z");

        Message saved = repository.save(Message.compose(55L, 1L, 8L, "hello", now));

        assertThat(saved.getId()).isEqualTo(101L);
        assertThat(saved.getConversationId()).isEqualTo(55L);
        assertThat(saved.getSenderId()).isEqualTo(1L);
        assertThat(saved.getReceiverId()).isEqualTo(8L);
        assertThat(saved.getContent()).isEqualTo("hello");
        assertThat(saved.getCreatedAt()).isEqualTo(now);
        assertThat(saved.getReadAt()).isNull();
    }

    @Test
    @DisplayName("会话内分页：行映射为消息实体，origin 顺序原样保留")
    void pageByConversationMapsRows() {
        Instant now = Instant.parse("2026-10-24T12:00:00Z");
        MessageDO first = new MessageDO();
        first.setId(2L);
        first.setConversationId(55L);
        first.setSenderId(8L);
        first.setReceiverId(1L);
        first.setContent("第二条");
        first.setCreatedAt(now);
        first.setReadAt(null);
        MessageDO second = new MessageDO();
        second.setId(1L);
        second.setConversationId(55L);
        second.setSenderId(1L);
        second.setReceiverId(8L);
        second.setContent("第一条");
        second.setCreatedAt(now.minusSeconds(60));
        second.setReadAt(now);
        when(messageMapper.pageByConversation(55L, 0, 20)).thenReturn(List.of(first, second));

        List<Message> messages = repository.pageByConversation(55L, 0, 20);

        assertThat(messages).extracting(Message::getId).containsExactly(2L, 1L);
        assertThat(messages.get(0).getContent()).isEqualTo("第二条");
        assertThat(messages.get(1).getReadAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("标记已读：只命中 conversationId + receiverId + read_at IS NULL 的行")
    void markReadUsesScopedConditions() {
        when(messageMapper.update(isNull(), any())).thenReturn(2);

        int updated = repository.markRead(55L, 1L, Instant.parse("2026-10-24T12:00:00Z"));

        assertThat(updated).isEqualTo(2);
        verify(messageMapper).update(isNull(), any());
    }

    @Test
    @DisplayName("计数：会话总数与未读总数分别透传")
    void countsDelegate() {
        when(messageMapper.countByConversation(55L)).thenReturn(3L);
        when(messageMapper.countUnread(1L)).thenReturn(4L);

        assertThat(repository.countByConversation(55L)).isEqualTo(3L);
        assertThat(repository.countUnread(1L)).isEqualTo(4L);
    }
}
