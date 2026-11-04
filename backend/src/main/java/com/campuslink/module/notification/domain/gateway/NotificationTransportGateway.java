package com.campuslink.module.notification.domain.gateway;

import com.campuslink.module.notification.domain.model.NotificationRequestedV1;

public interface NotificationTransportGateway {
    /** Returns only after a positive broker confirm with no returned message. */
    void publish(NotificationRequestedV1 event);
}
