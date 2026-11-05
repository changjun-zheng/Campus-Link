package com.campuslink.module.assistant.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantConversationTest {

    @Test
    @DisplayName("新建会话：id 非空、消息为空、用户轮次为 0")
    void startsEmpty() {
        AssistantConversation conversation = AssistantConversation.start(7L, 42L, "标题", "技术问答", "选中内容");

        assertThat(conversation.id()).isNotBlank();
        assertThat(conversation.userId()).isEqualTo(7L);
        assertThat(conversation.postId()).isEqualTo(42L);
        assertThat(conversation.postTitle()).isEqualTo("标题");
        assertThat(conversation.boardName()).isEqualTo("技术问答");
        assertThat(conversation.selectedText()).isEqualTo("选中内容");
        assertThat(conversation.messages()).isEmpty();
        assertThat(conversation.userTurnCount()).isZero();
    }

    @Test
    @DisplayName("追加消息返回新对象，原会话不被污染")
    void appendIsImmutable() {
        AssistantConversation original = AssistantConversation.start(7L, 42L, "标题", "版块", "选中");

        AssistantConversation first = original.withUserMessage("第一问");
        AssistantConversation second = first.withAssistantMessage("第一答");

        assertThat(original.messages()).isEmpty();
        assertThat(first.messages()).hasSize(1);
        assertThat(second.messages()).hasSize(2);
        assertThat(second.userTurnCount()).isEqualTo(1);
        assertThat(second.messages().get(1).role()).isEqualTo(AssistantMessage.Role.ASSISTANT);
    }

    @Test
    @DisplayName("轮次上限只数用户消息")
    void userTurnLimitCountsOnlyUserMessages() {
        AssistantConversation conversation = AssistantConversation.start(7L, 42L, "标题", "版块", "选中")
                .withUserMessage("一")
                .withAssistantMessage("答一")
                .withUserMessage("二");

        assertThat(conversation.userTurnCount()).isEqualTo(2);
        assertThat(conversation.reachedUserTurnLimit(2)).isTrue();
        assertThat(conversation.reachedUserTurnLimit(3)).isFalse();
    }

    @Test
    @DisplayName("restore 保留原 id / 时间戳 / 消息，供 Redis 反序列化")
    void restoreKeepsStoredData() {
        Instant created = Instant.parse("2026-11-05T08:00:00Z");
        Instant updated = Instant.parse("2026-11-05T08:10:00Z");

        AssistantConversation restored = AssistantConversation.restore("id-1", 7L, 42L, "标题", "版块", "选中",
                List.of(new AssistantMessage(AssistantMessage.Role.USER, "问")), created, updated);

        assertThat(restored.id()).isEqualTo("id-1");
        assertThat(restored.createdAt()).isEqualTo(created);
        assertThat(restored.updatedAt()).isEqualTo(updated);
        assertThat(restored.messages()).hasSize(1);
    }
}
