package com.campuslink.module.assistant.domain.service;

import com.campuslink.module.assistant.domain.gateway.ChatModelGateway.ChatRequest;
import com.campuslink.module.assistant.domain.model.AssistantConversation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantPromptBuilderTest {

    private final AssistantPromptBuilder builder = new AssistantPromptBuilder();

    @Test
    @DisplayName("系统提示包含帖子上下文、选中内容与不可信数据约束")
    void systemPromptCarriesContextAndGuard() {
        AssistantConversation conversation = AssistantConversation.start(1L, 2L, "为什么快排是 O(n log n)", "技术问答",
                        "最坏情况会退化成 O(n^2)")
                .withUserMessage("这段是什么意思？");

        ChatRequest request = builder.build(conversation, 800, 0.2);

        assertThat(request.systemPrompt())
                .contains("为什么快排是 O(n log n)")
                .contains("技术问答")
                .contains("最坏情况会退化成 O(n^2)")
                .contains("不可信数据")
                .contains("SELECTED_");
        assertThat(request.maxTokens()).isEqualTo(800);
        assertThat(request.temperature()).isEqualTo(0.2);
    }

    @Test
    @DisplayName("历史消息按 user / assistant 角色映射，系统提示不混进 messages")
    void mapsHistoryRoles() {
        AssistantConversation conversation = AssistantConversation.start(1L, 2L, "标题", "版块", "选中")
                .withUserMessage("第一问")
                .withAssistantMessage("第一答")
                .withUserMessage("第二问");

        ChatRequest request = builder.build(conversation, 800, 0.2);

        assertThat(request.messages()).hasSize(3);
        assertThat(request.messages().get(0).role()).isEqualTo("user");
        assertThat(request.messages().get(1).role()).isEqualTo("assistant");
        assertThat(request.messages().get(2).content()).isEqualTo("第二问");
        assertThat(request.messages()).noneMatch(message -> message.content().contains("不可信数据"));
    }

    @Test
    @DisplayName("每次构建使用不同随机分隔符，选中文本无法预判边界")
    void delimiterIsRandomPerBuild() {
        AssistantConversation conversation = AssistantConversation.start(1L, 2L, "标题", "版块", "选中")
                .withUserMessage("问");

        String first = builder.build(conversation, 100, 0.1).systemPrompt();
        String second = builder.build(conversation, 100, 0.1).systemPrompt();

        assertThat(first).isNotEqualTo(second);
    }
}
