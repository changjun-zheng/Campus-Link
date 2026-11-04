package com.campuslink.module.notification.infrastructure.messaging;

import com.campuslink.module.notification.domain.gateway.NotificationTransportGateway;
import com.campuslink.module.notification.domain.model.NotificationRequestedV1;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "campuslink.notifications.rabbitmq.enabled", havingValue = "true")
public class RabbitNotificationTransport implements NotificationTransportGateway {
    private final RabbitTemplate rabbit;
    private final ObjectMapper json;

    public RabbitNotificationTransport(RabbitTemplate rabbit, ObjectMapper json) {
        this.rabbit = rabbit;
        this.json = json;
    }

    @Override
    public void publish(NotificationRequestedV1 event) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setMessageId(event.eventId());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        sendConfirmed(NotificationRabbitTopology.EXCHANGE,
                new Message(json.writeValueAsBytes(event), properties));
    }

    public void sendConfirmed(String exchange, Message message) {
        CorrelationData correlation = new CorrelationData(UUID.randomUUID().toString());
        rabbit.send(exchange, NotificationRabbitTopology.ROUTING_KEY, message, correlation);
        try {
            var confirm = correlation.getFuture().get(3, TimeUnit.SECONDS);
            if (!confirm.isAck() || correlation.getReturned() != null) {
                throw new IllegalStateException("RabbitMQ publish not routed and confirmed");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("RabbitMQ confirm interrupted", ex);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException ex) {
            throw new IllegalStateException("RabbitMQ confirm unavailable", ex);
        }
    }
}
