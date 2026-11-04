package com.campuslink.module.notification.domain.gateway;

import com.campuslink.module.notification.domain.model.NotificationRequestedV1;
import java.util.List;

public interface NotificationOutboxGateway {
    void append(NotificationRequestedV1 event);
    List<Claim> claim();
    void sent(Claim claim);
    void retry(Claim claim, String error);
    Backlog backlog();

    record Claim(NotificationRequestedV1 event, String leaseToken, int attempts) { }
    record Backlog(long pending, long oldestAgeSeconds) { }
}
