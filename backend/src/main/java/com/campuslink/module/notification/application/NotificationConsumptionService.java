package com.campuslink.module.notification.application;

import com.campuslink.module.account.application.UserBlockApplicationService;
import com.campuslink.module.notification.application.event.NotificationCreatedEvent;
import com.campuslink.module.notification.domain.gateway.NotificationReceiptGateway;
import com.campuslink.module.notification.domain.gateway.NotificationRepository;
import com.campuslink.module.notification.domain.model.Notification;
import com.campuslink.module.notification.domain.model.NotificationRequestedV1;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationConsumptionService {
    private final NotificationReceiptGateway receipts;
    private final NotificationRepository notifications;
    private final UserBlockApplicationService blocks;
    private final ApplicationEventPublisher events;

    @Transactional
    public void consume(NotificationRequestedV1 event) {
        if (!receipts.acquire(event.eventId())) {
            return;
        }
        if (event.actorId() == event.recipientId() || blocks.isBlocked(event.recipientId(), event.actorId())) {
            return;
        }
        notifications.save(Notification.rehydrate(null, event.recipientId(), event.type(), event.actorId(),
                event.targetType(), event.targetId(), false, event.occurredAt()));
        events.publishEvent(new NotificationCreatedEvent(event.recipientId()));
    }
}
