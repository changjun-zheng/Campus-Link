package com.campuslink.module.notification.infrastructure.messaging;

import com.campuslink.module.notification.application.NotificationConsumptionService;
import com.campuslink.module.notification.domain.model.NotificationRequestedV1;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "campuslink.notifications.rabbitmq.enabled", havingValue = "true")
public class NotificationRabbitListener {
    private final NotificationConsumptionService consumer;
    private final RabbitNotificationTransport transport;
    private final ObjectMapper json;
    private final MeterRegistry meters;

    public NotificationRabbitListener(NotificationConsumptionService consumer, RabbitNotificationTransport transport,
            ObjectMapper json, MeterRegistry meters) {
        this.consumer = consumer;
        this.transport = transport;
        this.json = json;
        this.meters = meters;
    }

    @RabbitListener(queues = NotificationRabbitTopology.QUEUE, containerFactory = "notificationListenerFactory")
    public void receive(Message message) {
        // AUTO ack happens only after this synchronous method (and its DB transaction) returns.
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                consumer.consume(json.readValue(message.getBody(), NotificationRequestedV1.class));
                return;
            } catch (RuntimeException ex) {
                meters.counter("campuslink.notification.consume.failures").increment();
                if (attempt == 3) {
                    // Confirm recovery before acknowledging original. A recovery failure escapes and requeues it.
                    transport.sendConfirmed(NotificationRabbitTopology.ERROR_EXCHANGE, message);
                    meters.counter("campuslink.notification.dead.letters").increment();
                    return;
                }
                try {
                    Thread.sleep(attempt * 1000L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Notification consumer interrupted", interrupted);
                }
            }
        }
    }
}
