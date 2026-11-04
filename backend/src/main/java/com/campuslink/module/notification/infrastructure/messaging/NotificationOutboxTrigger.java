package com.campuslink.module.notification.infrastructure.messaging;

import com.campuslink.module.notification.application.NotificationOutboxService;
import com.campuslink.module.notification.domain.gateway.NotificationOutboxGateway;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "campuslink.notifications.rabbitmq.enabled", havingValue = "true")
public class NotificationOutboxTrigger {
    private static final Logger LOG = LoggerFactory.getLogger(NotificationOutboxTrigger.class);
    private final NotificationOutboxService service;
    private final NotificationOutboxGateway outbox;
    private final RabbitAdmin admin;
    private volatile long pending;
    private volatile long oldestAge;
    private volatile long dead;
    private long lastWarning;

    public NotificationOutboxTrigger(NotificationOutboxService service, NotificationOutboxGateway outbox,
            RabbitAdmin admin, MeterRegistry meters) {
        this.service = service;
        this.outbox = outbox;
        this.admin = admin;
        meters.gauge("campuslink.notification.outbox.pending", this, instance -> instance.pending);
        meters.gauge("campuslink.notification.outbox.oldest.seconds", this, instance -> instance.oldestAge);
        meters.gauge("campuslink.notification.dead.pending", this, instance -> instance.dead);
    }

    @Scheduled(fixedDelay = 1000, scheduler = "notificationTaskScheduler")
    public void publish() {
        service.publishPending();
    }

    @Scheduled(fixedDelay = 10000, scheduler = "notificationMonitorScheduler")
    public void monitor() {
        var backlog = outbox.backlog();
        pending = backlog.pending();
        oldestAge = backlog.oldestAgeSeconds();
        try {
            var info = admin.getQueueInfo(NotificationRabbitTopology.ERROR_QUEUE);
            dead = info == null ? -1 : info.getMessageCount();
        } catch (RuntimeException ex) {
            dead = -1;
        }
        long now = System.currentTimeMillis();
        if ((oldestAge > 60 || dead > 0 || dead < 0) && now - lastWarning >= 60000) {
            LOG.warn("Notification backlog: pending={}, oldestSeconds={}, dead={}", pending, oldestAge, dead);
            lastWarning = now;
        }
    }
}
