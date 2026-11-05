package com.campuslink.module.assistant.web.vo;

import com.campuslink.module.assistant.application.cmd.AssistantCommands;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 开启一轮「选中文本问 AI」（CR-200）。长度上限按配置在 application 层校验。 */
public record StartConversationRequest(@NotNull Long postId, @NotBlank String selectedText,
        @NotBlank String question) {

    public AssistantCommands.StartConversation toCommand() {
        return new AssistantCommands.StartConversation(postId, selectedText, question);
    }
}
