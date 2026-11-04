package com.campuslink.config;

import com.campuslink.module.notification.application.NotificationDeliverySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
public class NotificationDeliveryConfig {
    @Bean
    public NotificationDeliverySettings notificationDeliverySettings(Environment environment) {
        return new NotificationDeliverySettings(environment.getProperty(
                "campuslink.notifications.rabbitmq.enabled", Boolean.class, false),
                environment.getProperty("campuslink.notifications.delivery", "sync"));
    }
}
