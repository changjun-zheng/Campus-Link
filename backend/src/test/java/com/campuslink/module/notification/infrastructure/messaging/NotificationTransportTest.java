package com.campuslink.module.notification.infrastructure.messaging;

import com.campuslink.module.notification.application.NotificationConsumptionService;
import com.campuslink.module.notification.domain.model.NotificationRequestedV1;
import com.campuslink.module.notification.domain.model.NotificationTargetType;
import com.campuslink.module.notification.domain.model.NotificationType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;

class NotificationTransportTest {
    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final JsonMapper json = JsonMapper.builder().build();
    private final RabbitNotificationTransport transport = new RabbitNotificationTransport(rabbit, json);

    @Test
    void positiveConfirmRequiredAndPersistent() {
        doAnswer(invocation -> {
            Message message = invocation.getArgument(2);
            assertThat(message.getMessageProperties().getDeliveryMode()).isEqualTo(
                    org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
            assertThat(json.readValue(message.getBody(), NotificationRequestedV1.class).type())
                    .isEqualTo(NotificationType.LIKE);
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        transport.publish(new NotificationRequestedV1(UUID.randomUUID().toString(), 1, NotificationType.LIKE,
                1, 2, NotificationTargetType.POST, 1, Instant.now()));
    }

    @Test
    void nackAndReturnNeverCountAsSuccess() {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "nack"));
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        var message = new Message(new byte[0], new MessageProperties());
        assertThatThrownBy(() -> transport.sendConfirmed("missing", message)).isInstanceOf(IllegalStateException.class);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.setReturned(new ReturnedMessage(message, 312, "NO_ROUTE", "exchange", "key"));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        assertThatThrownBy(() -> transport.sendConfirmed("unbound", message)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void missingConfirmFailsWithinConfiguredTimeout() {
        assertThatThrownBy(() -> transport.sendConfirmed("exchange",
                new Message(new byte[0], new MessageProperties())))
                .isInstanceOf(IllegalStateException.class).hasMessage("RabbitMQ confirm unavailable");
    }

    @Test
    void malformedMessagesRetryThenConfirmedDeadLetter() {
        var consumer = mock(NotificationConsumptionService.class);
        var recoverer = mock(RabbitNotificationTransport.class);
        var meters = new SimpleMeterRegistry();
        var listener = new NotificationRabbitListener(consumer, recoverer, json, meters);
        var message = new Message("invalid-json".getBytes(java.nio.charset.StandardCharsets.UTF_8), new MessageProperties());
        listener.receive(message);
        verify(recoverer).sendConfirmed(NotificationRabbitTopology.ERROR_EXCHANGE, message);
        assertThat(meters.counter("campuslink.notification.consume.failures").count()).isEqualTo(3);
        verifyNoInteractions(consumer);
    }

    @Test
    void deadLetterFailureEscapesForOriginalRequeue() {
        var recoverer = mock(RabbitNotificationTransport.class);
        doThrow(new IllegalStateException()).when(recoverer).sendConfirmed(anyString(), any());
        var listener = new NotificationRabbitListener(mock(NotificationConsumptionService.class), recoverer,
                json, new SimpleMeterRegistry());
        var message = new Message(new byte[0], new MessageProperties());
        assertThatThrownBy(() -> listener.receive(message)).isInstanceOf(IllegalStateException.class);
    }
}
