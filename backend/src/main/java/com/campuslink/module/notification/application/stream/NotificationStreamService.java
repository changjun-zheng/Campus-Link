package com.campuslink.module.notification.application.stream;

import com.campuslink.module.notification.domain.gateway.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 通知实时流注册表（F-010）：按 userId 分组保存订阅，通知落库后推送最新未读数。
 *
 * <p><b>单实例内存表</b>：部署形态（A3-8）未定，多实例广播需要 Redis Pub/Sub，
 * 属另一条工程线；当前单机内存实现与既有 {@code @Scheduled} 热榜同口径。
 *
 * <p><b>每用户上限 {@value #MAX_CONNECTIONS_PER_USER} 条</b>：超出时关闭最早一条，
 * 防止同一账号无限开流耗尽线程；客户端断线后 5 秒重连。
 *
 * <p><b>失效即摘除</b>：订阅者的 {@code onUnread} / {@code onHeartbeat} 抛出运行时异常
 * （适配器把 {@code IOException} 包成 {@code UncheckedIOException}）时，本次推送当场移除该订阅。
 * 不在这里做重试——下一次通知或心跳不会再命中它。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationStreamService {

    /** 同一用户允许的并发流数量（多标签页场景留余量，超限淘汰最早一条） */
    static final int MAX_CONNECTIONS_PER_USER = 5;

    private final NotificationRepository notificationRepository;
    private final Map<Long, Deque<NotificationSubscription>> subscriptions = new ConcurrentHashMap<>();

    /**
     * 注册订阅，返回**幂等**退订句柄（{@code onCompletion} / {@code onTimeout} / {@code onError}
     * 可能先后各触发一次，只能真正移除一次）。
     */
    public Runnable subscribe(long userId, NotificationSubscription subscription) {
        Deque<NotificationSubscription> deque = subscriptions.computeIfAbsent(userId, key -> new ArrayDeque<>());
        List<NotificationSubscription> evicted = new ArrayList<>();
        synchronized (deque) {
            while (deque.size() >= MAX_CONNECTIONS_PER_USER) {
                evicted.add(deque.removeFirst());
            }
            deque.addLast(subscription);
        }
        // 关闭动作放到锁外：onClose 可能同步触发传输层的完成回调（再回到 remove）
        evicted.forEach(this::closeQuietly);
        AtomicBoolean active = new AtomicBoolean(true);
        return () -> {
            if (active.compareAndSet(true, false)) {
                remove(userId, subscription);
            }
        };
    }

    /** 推送某用户的最新未读数；无订阅时连库都不查 */
    public void publishUnread(long userId) {
        Deque<NotificationSubscription> deque = subscriptions.get(userId);
        if (deque == null) {
            return;
        }
        long unreadCount = notificationRepository.countUnread(userId);
        for (NotificationSubscription subscription : snapshot(deque)) {
            try {
                subscription.onUnread(unreadCount);
            } catch (RuntimeException e) {
                drop(userId, subscription, e);
            }
        }
    }

    /** 心跳：保活所有订阅，顺带清理失效连接 */
    public void heartbeat() {
        for (Map.Entry<Long, Deque<NotificationSubscription>> entry : subscriptions.entrySet()) {
            for (NotificationSubscription subscription : snapshot(entry.getValue())) {
                try {
                    subscription.onHeartbeat();
                } catch (RuntimeException e) {
                    drop(entry.getKey(), subscription, e);
                }
            }
        }
    }

    /** 包内可见：供单测断言连接上限与退订生效，不对外暴露 */
    int activeCount(long userId) {
        Deque<NotificationSubscription> deque = subscriptions.get(userId);
        if (deque == null) {
            return 0;
        }
        synchronized (deque) {
            return deque.size();
        }
    }

    private static List<NotificationSubscription> snapshot(Deque<NotificationSubscription> deque) {
        synchronized (deque) {
            return List.copyOf(deque);
        }
    }

    private void closeQuietly(NotificationSubscription subscription) {
        try {
            subscription.onClose();
        } catch (RuntimeException e) {
            log.debug("关闭旧通知流订阅失败（忽略）", e);
        }
    }

    private void drop(long userId, NotificationSubscription subscription, RuntimeException cause) {
        log.debug("通知流推送失败，摘除订阅 userId={}", userId, cause);
        remove(userId, subscription);
    }

    private void remove(long userId, NotificationSubscription subscription) {
        Deque<NotificationSubscription> deque = subscriptions.get(userId);
        if (deque == null) {
            return;
        }
        boolean empty;
        synchronized (deque) {
            deque.remove(subscription);
            empty = deque.isEmpty();
        }
        if (empty) {
            // 只删"仍然是这个空 deque"的映射，避免并发 subscribe 刚建的新 deque 被误删
            subscriptions.remove(userId, deque);
        }
    }
}
