package com.campuslink.module.notification.infrastructure.stream;

import com.campuslink.module.notification.application.stream.NotificationStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 通知流心跳触发器（F-010）：只负责"何时跑"，发送口径在 application 的
 * {@link NotificationStreamService#heartbeat()}；不在触发器里写业务规则。
 */
@Component
@RequiredArgsConstructor
public class NotificationStreamHeartbeatTrigger {

    private final NotificationStreamService notificationStreamService;

    @Scheduled(fixedDelayString = "${campuslink.notification.stream-heartbeat-ms:20000}")
    public void heartbeat() {
        notificationStreamService.heartbeat();
    }
}
