package com.campuslink.module.assistant.application;

/**
 * 已通过预检的一轮问答（CR-200）。Controller 先拿到 handle 再创建 SSE，确保配额 / 机审 /
 * 帖子可见性失败时仍返回普通 JSON 错误，而不是已经开始的半截事件流。
 */
public interface AssistantStreamHandle {

    /** 提交到专用线程池并开始推送；线程池拒绝时抛 {@code ASSISTANT_UNAVAILABLE}。 */
    void start(AssistantStreamObserver observer);

    /** 客户端停止 / 断开 / 超时：取消上游读取并释放并发锁。可重复调用。 */
    void cancel();
}
