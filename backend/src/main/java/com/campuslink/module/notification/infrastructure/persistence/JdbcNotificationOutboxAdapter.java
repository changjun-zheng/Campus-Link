package com.campuslink.module.notification.infrastructure.persistence;

import com.campuslink.module.notification.domain.gateway.NotificationOutboxGateway;
import com.campuslink.module.notification.domain.gateway.NotificationReceiptGateway;
import com.campuslink.module.notification.domain.model.NotificationRequestedV1;
import com.campuslink.module.notification.domain.model.NotificationTargetType;
import com.campuslink.module.notification.domain.model.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JdbcNotificationOutboxAdapter implements NotificationOutboxGateway, NotificationReceiptGateway {
    private final JdbcTemplate jdbc;

    @Override
    public void append(NotificationRequestedV1 event) {
        jdbc.update("""
                INSERT INTO notification_outbox
                (event_id, schema_version, type, actor_id, recipient_id, target_type, target_id, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, event.eventId(), event.schemaVersion(), event.type().code(), event.actorId(), event.recipientId(),
                event.targetType().name(), event.targetId(), Timestamp.from(event.occurredAt()));
    }

    @Override
    @Transactional
    public List<Claim> claim() {
        String token = UUID.randomUUID().toString();
        List<Claim> claims = jdbc.query("""
                SELECT * FROM notification_outbox
                WHERE (status='PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP(6))
                   OR (status='INFLIGHT' AND locked_until <= CURRENT_TIMESTAMP(6))
                ORDER BY occurred_at, event_id LIMIT 10 FOR UPDATE SKIP LOCKED
                """, (rs, row) -> new Claim(new NotificationRequestedV1(rs.getString("event_id"),
                rs.getInt("schema_version"), NotificationType.fromCode(rs.getString("type")),
                rs.getLong("actor_id"), rs.getLong("recipient_id"),
                NotificationTargetType.fromName(rs.getString("target_type")), rs.getLong("target_id"),
                rs.getTimestamp("occurred_at").toInstant()), token, rs.getInt("attempts") + 1));
        for (Claim claim : claims) {
            jdbc.update("""
                    UPDATE notification_outbox SET status='INFLIGHT', lease_token=?,
                    locked_until=DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL 60 SECOND), attempts=attempts+1
                    WHERE event_id=?
                    """, token, claim.event().eventId());
        }
        return claims;
    }

    @Override
    public void sent(Claim claim) {
        jdbc.update("""
                UPDATE notification_outbox SET status='SENT', sent_at=CURRENT_TIMESTAMP(6),
                locked_until=NULL, lease_token=NULL, last_error=NULL
                WHERE event_id=? AND status='INFLIGHT' AND lease_token=?
                """, claim.event().eventId(), claim.leaseToken());
    }

    @Override
    public void retry(Claim claim, String error) {
        long delay = Math.min(60L, 1L << Math.min(6, claim.attempts() - 1));
        jdbc.update("""
                UPDATE notification_outbox SET status='PENDING', locked_until=NULL, lease_token=NULL,
                next_attempt_at=DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL ? SECOND), last_error=?
                WHERE event_id=? AND status='INFLIGHT' AND lease_token=?
                """, delay, error, claim.event().eventId(), claim.leaseToken());
    }

    @Override
    public Backlog backlog() {
        return jdbc.queryForObject("""
                SELECT COUNT(*) AS pending,
                COALESCE(GREATEST(0, TIMESTAMPDIFF(SECOND, MIN(occurred_at), CURRENT_TIMESTAMP(6))),0) AS age
                FROM notification_outbox WHERE status <> 'SENT'
                """, (rs, row) -> new Backlog(rs.getLong("pending"), rs.getLong("age")));
    }

    @Override
    public boolean acquire(String eventId) {
        return jdbc.update("INSERT IGNORE INTO notification_receipts(event_id) VALUES (?)", eventId) == 1;
    }
}
