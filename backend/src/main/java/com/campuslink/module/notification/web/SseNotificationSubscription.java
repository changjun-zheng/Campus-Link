package com.campuslink.module.notification.web;

import com.campuslink.module.notification.application.cmd.NotificationResults.UnreadCountResult;
import com.campuslink.module.notification.application.stream.NotificationSubscription;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * {@link NotificationSubscription} 的 SSE 适配器（web 层）：把应用层的"推未读数 / 心跳 / 关闭"
 * 翻译成 {@code event: unread} 帧与注释心跳帧。
 *
 * <p>发送失败统一包成 {@link UncheckedIOException}，由注册表摘除订阅——传输层异常不向业务层扩散。
 */
class SseNotificationSubscription implements NotificationSubscription {

    private final SseEmitter emitter;

    SseNotificationSubscription(SseEmitter emitter) {
        this.emitter = emitter;
    }

    @Override
    public void onUnread(long unreadCount) {
        send(SseEmitter.event()
                .name("unread")
                .data(new UnreadCountResult(unreadCount), MediaType.APPLICATION_JSON));
    }

    @Override
    public void onHeartbeat() {
        send(SseEmitter.event().comment("ping"));
    }

    @Override
    public void onClose() {
        try {
            emitter.complete();
        } catch (RuntimeException ignored) {
            // 已完成 / 已出错的连接再关闭一次不构成错误
        }
    }

    private void send(SseEmitter.SseEventBuilder builder) {
        try {
            emitter.send(builder);
        } catch (IOException e) {
            throw new UncheckedIOException("通知流发送失败", e);
        }
    }
}
