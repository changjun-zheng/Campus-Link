package com.campuslink.module.notification.application;

import com.campuslink.module.account.application.UserBlockApplicationService;
import com.campuslink.module.notification.domain.gateway.NotificationOutboxGateway;
import com.campuslink.module.notification.domain.gateway.NotificationReceiptGateway;
import com.campuslink.module.notification.domain.gateway.NotificationRepository;
import com.campuslink.module.notification.domain.gateway.NotificationTransportGateway;
import com.campuslink.module.notification.domain.model.Notification;
import com.campuslink.module.notification.domain.model.NotificationRequestedV1;
import com.campuslink.module.notification.domain.model.NotificationTargetType;
import com.campuslink.module.notification.domain.model.NotificationType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;

class NotificationDeliveryTest {
    private final NotificationReceiptGateway receipts = mock(NotificationReceiptGateway.class);
    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final UserBlockApplicationService blocks = mock(UserBlockApplicationService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final NotificationConsumptionService consumer = new NotificationConsumptionService(
            receipts, repository, blocks, events);

    private NotificationRequestedV1 event(long actor, long recipient) {
        return new NotificationRequestedV1(UUID.randomUUID().toString(), 1, NotificationType.REPLY,
                actor, recipient, NotificationTargetType.REPLY, 10L, Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void invalidCombinationFails() {
        assertThatThrownBy(() -> new NotificationDeliverySettings(false, "rabbitmq"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NotificationDeliverySettings(true, "typo"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new NotificationDeliverySettings(true, "sync").asynchronous()).isFalse();
    }

    @Test
    void invalidEventRejected() {
        var valid = event(1, 2);
        assertThatThrownBy(() -> new NotificationRequestedV1(valid.eventId(), 2, valid.type(), 1, 2,
                valid.targetType(), 10, valid.occurredAt())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NotificationRequestedV1(valid.eventId(), 1, NotificationType.CONTENT_REMOVED,
                1, 2, valid.targetType(), 10, valid.occurredAt())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void duplicateDoesNotWriteOrPush() {
        consumer.consume(event(1, 2));
        verifyNoInteractions(repository, blocks, events);
    }

    @Test
    void consumedEventKeepsOriginalTime() {
        var event = event(1, 2);
        when(receipts.acquire(event.eventId())).thenReturn(true);
        consumer.consume(event);
        var captor = org.mockito.ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getCreatedAt()).isEqualTo(event.occurredAt());
        verify(events).publishEvent(any(Object.class));
    }

    @Test
    void selfAndBlockedSuppressedAfterReceipt() {
        var self = event(1, 1);
        when(receipts.acquire(self.eventId())).thenReturn(true);
        consumer.consume(self);
        var blocked = event(1, 2);
        when(receipts.acquire(blocked.eventId())).thenReturn(true);
        when(blocks.isBlocked(2, 1)).thenReturn(true);
        consumer.consume(blocked);
        verifyNoInteractions(repository, events);
    }

    @Test
    void brokerFailureSchedulesRetryAndContinues() {
        var outbox = mock(NotificationOutboxGateway.class);
        var transport = mock(NotificationTransportGateway.class);
        var first = new NotificationOutboxGateway.Claim(event(1, 2), "lease", 1);
        var second = new NotificationOutboxGateway.Claim(event(1, 2), "lease", 1);
        when(outbox.claim()).thenReturn(List.of(first, second));
        doThrow(new IllegalStateException()).when(transport).publish(first.event());
        var meters = new SimpleMeterRegistry();
        new NotificationOutboxService(outbox, transport, meters).publishPending();
        verify(outbox).retry(first, "IllegalStateException");
        verify(outbox, never()).sent(first);
        verify(outbox).sent(second);
        assertThat(meters.counter("campuslink.notification.publish.failures").count()).isEqualTo(1);
    }
}
