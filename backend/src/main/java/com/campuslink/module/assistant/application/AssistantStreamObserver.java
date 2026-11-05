package com.campuslink.module.assistant.application;

import com.campuslink.common.result.ResultCode;

/**
 * 流式事件出口（CR-200）：application 不依赖 {@code SseEmitter}，web 层负责翻译成 SSE 帧。
 *
 * <p>预检失败发生在流建立之前，仍由 {@code GlobalExceptionHandler} 产出 {@code ApiError}；
 * 只有流开始后的模型 / 输出机审错误才走 {@link #onError(ResultCode)}。
 */
public interface AssistantStreamObserver {

    void onMeta(String conversationId, int remainingToday);

    void onDelta(String text);

    void onDone(AssistantResults.AssistantDone done);

    void onError(ResultCode code);
}
