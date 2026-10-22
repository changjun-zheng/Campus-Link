package com.campuslink.module.notification.application.stream;

/**
 * 通知流订阅者（F-010）：application 层只表达"推什么"，
 * Servlet / {@code SseEmitter} 一类传输细节留在 web 适配器里。
 *
 * <p>{@link #onUnread(long)} 在传输已断开时可以抛 {@link RuntimeException}——
 * 注册表据此摘除失效订阅，避免向死连接重复写帧。
 */
public interface NotificationSubscription {

    /** 推送最新未读数（客户端据此更新顶栏角标） */
    void onUnread(long unreadCount);

    /** 心跳帧（保活；客户端不应把它当成计数变化） */
    void onHeartbeat();

    /** 服务端主动关闭（例如同用户连接数超限，淘汰最早一条） */
    void onClose();
}
