package com.campuslink.module.notification.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Stable, privacy-minimal wire event. One ID represents one interaction, not one target. */
public record NotificationRequestedV1(String eventId, int schemaVersion, NotificationType type,
        long actorId, long recipientId, NotificationTargetType targetType, long targetId, Instant occurredAt) {
    private static final Set<NotificationType> SUPPORTED = Set.of(NotificationType.REPLY, NotificationType.QUOTE,
            NotificationType.ACCEPT, NotificationType.LIKE, NotificationType.FAVORITE);

    public NotificationRequestedV1 {
        if (!UUID.fromString(eventId).toString().equals(eventId) || schemaVersion != 1 || !SUPPORTED.contains(type)
                || actorId <= 0 || recipientId <= 0 || targetId <= 0) {
            throw new IllegalArgumentException("Invalid notification event");
        }
        Objects.requireNonNull(targetType);
        Objects.requireNonNull(occurredAt);
    }

    public static boolean supports(NotificationType type) {
        return SUPPORTED.contains(type);
    }
}
