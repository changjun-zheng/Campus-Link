package com.campuslink.module.notification.application;

import com.campuslink.module.notification.domain.gateway.NotificationOutboxGateway;
import com.campuslink.module.notification.domain.gateway.NotificationTransportGateway;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Service
@ConditionalOnProperty(name = "campuslink.notifications.rabbitmq.enabled", havingValue = "true")
public class NotificationOutboxService {
    private final NotificationOutboxGateway outbox;
    private final NotificationTransportGateway transport;
    private final MeterRegistry meters;

    public NotificationOutboxService(NotificationOutboxGateway outbox, NotificationTransportGateway transport,
            MeterRegistry meters) {
        this.outbox = outbox;
        this.transport = transport;
        this.meters = meters;
    }

    public void publishPending() {
        for (var claim : outbox.claim()) {
            try {
                transport.publish(claim.event());
                outbox.sent(claim);
            } catch (RuntimeException ex) {
                meters.counter("campuslink.notification.publish.failures").increment();
                outbox.retry(claim, ex.getClass().getSimpleName());
            }
        }
    }
}
