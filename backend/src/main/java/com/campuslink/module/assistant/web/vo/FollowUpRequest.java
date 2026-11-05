package com.campuslink.module.assistant.web.vo;

import com.campuslink.module.assistant.application.cmd.AssistantCommands;
import jakarta.validation.constraints.NotBlank;

/** 当前会话内追问（CR-200）；conversationId 走路径，问题走请求体。 */
public record FollowUpRequest(@NotBlank String question) {

    public AssistantCommands.FollowUp toCommand(String conversationId) {
        return new AssistantCommands.FollowUp(conversationId, question);
    }
}
