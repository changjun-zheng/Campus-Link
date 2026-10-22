package com.campuslink.module.notification.infrastructure.stream;

import com.campuslink.module.notification.application.event.NotificationCreatedEvent;
import com.campuslink.module.notification.application.stream.NotificationStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 通知落库后推送到实时流（F-010）。
 *
 * <p>{@code AFTER_COMMIT}：通知与触发它的动作（回帖 / 点赞）同事务，提交后才推，
 * 避免事务回滚却已把角标推上去。{@code fallbackExecution = true} 覆盖无事务调用
 * （例如将来某个非事务入口直接发通知）——那种场景本来就没有"回滚"可言。
 */
@Component
@RequiredArgsConstructor
public class NotificationStreamListener {

    private final NotificationStreamService notificationStreamService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNotificationCreated(NotificationCreatedEvent event) {
        notificationStreamService.publishUnread(event.userId());
    }
}
