package com.campuslink.module.assistant.domain.gateway;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 模型流取消句柄（CR-200）：应用层在客户端停止 / 断开 / SSE 超时时调用 {@link #cancel()}，
 * 适配器在读取循环中检查，并关闭上游响应体，避免继续消耗 token。
 */
public final class ChatStreamControl {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile InputStream body;

    /** 适配器拿到响应体后立即注册；若此时已被取消，直接关闭。 */
    public void attach(InputStream input) {
        this.body = input;
        if (cancelled.get()) {
            closeQuietly();
        }
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public void cancel() {
        cancelled.set(true);
        closeQuietly();
    }

    private void closeQuietly() {
        InputStream current = body;
        if (current == null) {
            return;
        }
        try {
            current.close();
        } catch (IOException ignored) {
            // 关闭失败不改变"已取消"语义
        }
    }
}
