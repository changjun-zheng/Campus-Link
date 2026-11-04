package com.campuslink.module.notification.domain.gateway;

public interface NotificationReceiptGateway {
    /** Must join the transaction that writes the notification. */
    boolean acquire(String eventId);
}
