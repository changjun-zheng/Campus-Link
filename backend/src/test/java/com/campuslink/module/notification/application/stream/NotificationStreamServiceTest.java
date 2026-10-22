package com.campuslink.module.notification.application.stream;

import com.campuslink.module.notification.domain.gateway.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 通知实时流注册表（F-010）：用户隔离、退订幂等、心跳、失效摘除、每用户连接上限。
 * 传输层（SseEmitter）不在本测试范围内——适配器抛出的运行时异常在这里被当作"连接已坏"。
 */
@ExtendWith(MockitoExtension.class)
class NotificationStreamServiceTest {

    private static final long USER = 42L;
    private static final long OTHER_USER = 99L;

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationStreamService service;

    @Test
    @DisplayName("推送按用户隔离：只查目标用户的未读数，只触达目标用户的订阅")
    void publishIsolatedByUser() {
        RecordingSubscription mine = new RecordingSubscription();
        RecordingSubscription others = new RecordingSubscription();
        service.subscribe(USER, mine);
        service.subscribe(OTHER_USER, others);
        when(notificationRepository.countUnread(USER)).thenReturn(3L);

        service.publishUnread(USER);

        assertThat(mine.unreadCounts).containsExactly(3L);
        assertThat(others.unreadCounts).isEmpty();
        verify(notificationRepository).countUnread(USER);
        verify(notificationRepository, never()).countUnread(OTHER_USER);
    }

    @Test
    @DisplayName("退订幂等：重复调用只移除一次，之后既不再推送也不查库")
    void unsubscribeIsIdempotent() {
        RecordingSubscription subscription = new RecordingSubscription();
        Runnable unsubscribe = service.subscribe(USER, subscription);

        unsubscribe.run();
        unsubscribe.run();

        assertThat(service.activeCount(USER)).isZero();
        service.publishUnread(USER);
        assertThat(subscription.unreadCounts).isEmpty();
        verify(notificationRepository, never()).countUnread(anyLong());
    }

    @Test
    @DisplayName("心跳：触达所有订阅但不查未读数")
    void heartbeatReachesAllSubscriptions() {
        RecordingSubscription first = new RecordingSubscription();
        RecordingSubscription second = new RecordingSubscription();
        service.subscribe(USER, first);
        service.subscribe(USER, second);

        service.heartbeat();

        assertThat(first.heartbeats).isEqualTo(1);
        assertThat(second.heartbeats).isEqualTo(1);
        verify(notificationRepository, never()).countUnread(anyLong());
    }

    @Test
    @DisplayName("失效订阅当场摘除：坏连接只失败一次，后续推送只触达健康连接")
    void failedSubscriptionIsDropped() {
        RecordingSubscription broken = new RecordingSubscription();
        broken.failOnUnread = true;
        RecordingSubscription healthy = new RecordingSubscription();
        service.subscribe(USER, broken);
        service.subscribe(USER, healthy);
        when(notificationRepository.countUnread(USER)).thenReturn(1L, 2L);

        service.publishUnread(USER);
        service.publishUnread(USER);

        assertThat(service.activeCount(USER)).isEqualTo(1);
        assertThat(healthy.unreadCounts).containsExactly(1L, 2L);
        assertThat(broken.unreadCounts).isEmpty();
    }

    @Test
    @DisplayName("每用户上限：第 6 条连接把最早一条挤出（关闭帧），在册连接保持 5 条")
    void oldestConnectionIsEvictedBeyondLimit() {
        List<RecordingSubscription> subscriptions = new ArrayList<>();
        for (int i = 0; i <= NotificationStreamService.MAX_CONNECTIONS_PER_USER; i++) {
            RecordingSubscription subscription = new RecordingSubscription();
            subscriptions.add(subscription);
            service.subscribe(USER, subscription);
        }

        assertThat(service.activeCount(USER)).isEqualTo(NotificationStreamService.MAX_CONNECTIONS_PER_USER);
        assertThat(subscriptions.get(0).closes).isEqualTo(1);
        assertThat(subscriptions.get(1).closes).isZero();
    }

    private static final class RecordingSubscription implements NotificationSubscription {

        private final List<Long> unreadCounts = new ArrayList<>();
        private int heartbeats;
        private int closes;
        private boolean failOnUnread;

        @Override
        public void onUnread(long unreadCount) {
            if (failOnUnread) {
                throw new IllegalStateException("连接已断开");
            }
            unreadCounts.add(unreadCount);
        }

        @Override
        public void onHeartbeat() {
            heartbeats++;
        }

        @Override
        public void onClose() {
            closes++;
        }
    }
}
