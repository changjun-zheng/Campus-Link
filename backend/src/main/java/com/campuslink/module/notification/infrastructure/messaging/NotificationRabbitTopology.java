package com.campuslink.module.notification.infrastructure.messaging;

public final class NotificationRabbitTopology {
    public static final String EXCHANGE = "campuslink.notifications";
    public static final String ROUTING_KEY = "notification.requested.v1";
    public static final String QUEUE = "campuslink.notifications.v1";
    public static final String ERROR_EXCHANGE = "campuslink.notifications.errors";
    public static final String ERROR_QUEUE = "campuslink.notifications.dead.v1";
    private NotificationRabbitTopology() { }
}
