package com.campuslink.module.assistant.web;

import com.campuslink.common.result.ResultCode;
import com.campuslink.module.assistant.application.AssistantResults;
import com.campuslink.module.assistant.application.AssistantStreamObserver;
import com.campuslink.module.assistant.web.vo.AssistantEventVo;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SSE 事件出口（CR-200）：application 只认识 observer，不知道 {@link SseEmitter}。
 *
 * <p>发送失败说明客户端已断开：关闭 emitter（触发 Controller 注册的 onCompletion → handle.cancel），
 * 后续 delta 直接丢弃，不再反复尝试写一个已死的连接。
 */
class SseAssistantObserver implements AssistantStreamObserver {

    private final SseEmitter emitter;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    SseAssistantObserver(SseEmitter emitter) {
        this.emitter = emitter;
    }

    @Override
    public void onMeta(String conversationId, int remainingToday) {
        send("meta", new AssistantEventVo.Meta(conversationId, remainingToday));
    }

    @Override
    public void onDelta(String text) {
        send("delta", new AssistantEventVo.Delta(text));
    }

    @Override
    public void onDone(AssistantResults.AssistantDone done) {
        send("done", done);
        close();
    }

    @Override
    public void onError(ResultCode code) {
        send("error", new AssistantEventVo.Error(code.getCode(), code.getMessage()));
        close();
    }

    private void send(String event, Object payload) {
        if (closed.get()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(event).data(payload, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            close();
        }
    }

    private void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            emitter.complete();
        } catch (RuntimeException ignored) {
            // 已超时 / 已出错的连接再关闭一次不构成错误
        }
    }
}
