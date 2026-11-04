package com.campuslink.module.notification.application;

public record NotificationDeliverySettings(boolean rabbitmqEnabled, String delivery) {
    public NotificationDeliverySettings {
        if (!("sync".equals(delivery) || "rabbitmq".equals(delivery))) {
            throw new IllegalArgumentException("Notification delivery must be sync or rabbitmq");
        }
        if ("rabbitmq".equals(delivery) && !rabbitmqEnabled) {
            throw new IllegalArgumentException("RabbitMQ delivery requires rabbitmq.enabled=true");
        }
    }

    public boolean asynchronous() {
        return "rabbitmq".equals(delivery);
    }
}
