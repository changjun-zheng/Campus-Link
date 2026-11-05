package com.campuslink.module.assistant.application.cmd;

/** 问 AI 的 application 入参（CR-200）；web 层只做协议转换，不把校验规则写在 Controller。 */
public final class AssistantCommands {

    private AssistantCommands() {
    }

    public record StartConversation(long postId, String selectedText, String question) {
    }

    public record FollowUp(String conversationId, String question) {
    }
}
