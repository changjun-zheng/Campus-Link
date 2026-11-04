package com.campuslink.config;

import com.campuslink.module.notification.application.NotificationDeliverySettings;
import com.campuslink.module.notification.infrastructure.messaging.NotificationRabbitConfig;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class NotificationDeliveryConfigTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(NotificationDeliveryConfig.class, NotificationRabbitConfig.class);

    @Test
    void defaultSyncDoesNotCreateBrokerConnections() {
        context.run(ctx -> {
            assertThat(ctx).hasNotFailed().doesNotHaveBean(ConnectionFactory.class);
            assertThat(ctx.getBean(NotificationDeliverySettings.class).asynchronous()).isFalse();
        });
    }

    @Test
    void asyncWithoutIntegrationFailsAtStartup() {
        context.withPropertyValues("campuslink.notifications.delivery=rabbitmq").run(ctx ->
                assertThat(ctx).hasFailed());
    }

    @Test
    void enabledBrokerRequiresExternalCredentials() {
        context.withPropertyValues("campuslink.notifications.rabbitmq.enabled=true").run(ctx ->
                assertThat(ctx).hasFailed());
    }
}
