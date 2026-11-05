package com.campuslink.module.assistant.web.vo;

/** SSE 事件负载（CR-200）；完成帧复用 application 的 {@code AssistantDone}，错误帧用本类。 */
public final class AssistantEventVo {

    private AssistantEventVo() {
    }

    public record Meta(String conversationId, int remainingToday) {
    }

    public record Delta(String text) {
    }

    public record Error(int code, String message) {
    }
}
