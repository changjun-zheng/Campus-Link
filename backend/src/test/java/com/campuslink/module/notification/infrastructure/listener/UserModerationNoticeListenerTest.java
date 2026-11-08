package com.campuslink.module.notification.infrastructure.listener;

import com.campuslink.common.event.UserMutedEvent;
import com.campuslink.common.event.UserWarnedEvent;
import com.campuslink.module.notification.application.NotificationApplicationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** 账号治理事件 → 通知用例的委托（CR-209）：本类不含业务规则，只守住参数不被拆错。 */
class UserModerationNoticeListenerTest {

    private final NotificationApplicationService service = mock(NotificationApplicationService.class);
    private final UserModerationNoticeListener listener = new UserModerationNoticeListener(service);

    @Test
    @DisplayName("警告事件：操作人、目标与原因原样转交")
    void warnedEventDelegates() {
        listener.onWarned(new UserWarnedEvent(7L, 42L, "请勿发布联系方式"));

        verify(service).userWarned(7L, 42L, "请勿发布联系方式");
    }

    @Test
    @DisplayName("禁言事件：截止时间与原因原样转交")
    void mutedEventDelegates() {
        Instant until = Instant.parse("2026-11-09T04:00:00Z");

        listener.onMuted(new UserMutedEvent(7L, 42L, until, "刷屏"));

        verify(service).userMuted(7L, 42L, until, "刷屏");
    }
}
