package com.campuslink.module.notification.infrastructure.listener;

import com.campuslink.common.event.UserMutedEvent;
import com.campuslink.common.event.UserWarnedEvent;
import com.campuslink.module.notification.application.NotificationApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 账号治理通知监听（F-ADMIN-002 / CR-209）。
 *
 * <p>account 只发布 {@link UserWarnedEvent} / {@link UserMutedEvent}，通知落库由本上下文完成——
 * account 若直接依赖 {@code NotificationApplicationService}，会与 notification 已依赖
 * {@code AccountApplicationService} 形成构造器循环。
 *
 * <p>刻意用普通 {@code @EventListener} 而非 {@code @TransactionalEventListener(AFTER_COMMIT)}：
 * 与发帖 / 回帖通知同口径，通知与处罚同事务，写失败则整笔回滚，不出现"已处罚但用户无通知"。
 */
@Component
@RequiredArgsConstructor
public class UserModerationNoticeListener {

    private final NotificationApplicationService notificationApplicationService;

    @EventListener
    public void onWarned(UserWarnedEvent event) {
        notificationApplicationService.userWarned(event.operatorId(), event.targetUserId(), event.reason());
    }

    @EventListener
    public void onMuted(UserMutedEvent event) {
        notificationApplicationService.userMuted(event.operatorId(), event.targetUserId(),
                event.mutedUntil(), event.reason());
    }
}
