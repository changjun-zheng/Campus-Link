package com.campuslink.integration;

import com.campuslink.module.notification.application.NotificationConsumptionService;
import com.campuslink.module.notification.domain.gateway.NotificationOutboxGateway;
import com.campuslink.module.notification.domain.gateway.NotificationReceiptGateway;
import com.campuslink.module.notification.domain.model.NotificationRequestedV1;
import com.campuslink.module.notification.domain.model.NotificationTargetType;
import com.campuslink.module.notification.domain.model.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "SPRING_IT", matches = "true")
class NotificationOutboxIntegrationTest {
    @Autowired private NotificationOutboxGateway outbox;
    @Autowired private NotificationReceiptGateway receipts;
    @Autowired private NotificationConsumptionService consumer;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private com.campuslink.module.forum.application.PostApplicationService posts;
    @Autowired private com.campuslink.module.forum.application.ReplyApplicationService replies;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.campuslink.module.notification.application.NotificationDeliverySettings settings;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    private com.campuslink.module.notification.infrastructure.persistence.JdbcNotificationOutboxAdapter adapter;

    @Test
    void realReplyAndIntentRollbackTogether() {
        org.mockito.Mockito.when(settings.asynchronous()).thenReturn(true);
        long beforeReplies = rows("replies");
        long beforeOutbox = rows("notification_outbox");
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var ids = jdbc.queryForList("SELECT id FROM users ORDER BY id LIMIT 2", Long.class);
            var post = posts.publish(ids.get(0), new com.campuslink.module.forum.application.cmd.PublishPostCommand(
                    "qna", "outbox transaction probe", "transaction probe", java.util.List.of(), false));
            replies.reply(post.id(), ids.get(1),
                    new com.campuslink.module.forum.application.cmd.PublishReplyCommand("reply probe", null));
            assertThat(rows("notification_outbox")).isEqualTo(beforeOutbox + 1);
            tx.setRollbackOnly();
        });
        assertThat(rows("replies")).isEqualTo(beforeReplies);
        assertThat(rows("notification_outbox")).isEqualTo(beforeOutbox);
    }

    @Test
    void outboxFailureRollsBackRealReplyAndCounter() {
        org.mockito.Mockito.when(settings.asynchronous()).thenReturn(true);
        org.mockito.Mockito.doThrow(new IllegalStateException("injected outbox failure"))
                .when(adapter).append(org.mockito.ArgumentMatchers.any());
        long beforePosts = rows("posts");
        long beforeReplies = rows("replies");
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var ids = jdbc.queryForList("SELECT id FROM users ORDER BY id LIMIT 2", Long.class);
            var post = posts.publish(ids.get(0), new com.campuslink.module.forum.application.cmd.PublishPostCommand(
                    "qna", "outbox failure probe", "transaction probe", java.util.List.of(), false));
            replies.reply(post.id(), ids.get(1),
                    new com.campuslink.module.forum.application.cmd.PublishReplyCommand("reply probe", null));
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("injected outbox failure");
        assertThat(rows("posts")).isEqualTo(beforePosts);
        assertThat(rows("replies")).isEqualTo(beforeReplies);
    }

    private long rows(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private NotificationRequestedV1 event() {
        var ids = jdbc.queryForList("SELECT id FROM users ORDER BY id LIMIT 2", Long.class);
        return new NotificationRequestedV1(UUID.randomUUID().toString(), 1, NotificationType.LIKE,
                ids.get(0), ids.get(1), NotificationTargetType.POST, 999999L,
                Instant.parse("2026-01-02T03:04:05Z"));
    }

    @Test
    void intentAndBusinessRollbackTogether() {
        var event = event();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            receipts.acquire(event.eventId());
            outbox.append(event);
            tx.setRollbackOnly();
        });
        assertThat(count("notification_outbox", event.eventId())).isZero();
        assertThat(count("notification_receipts", event.eventId())).isZero();
    }

    @Test
    void consumeRollbackDoesNotLeaveDedupReceipt() {
        var event = event();
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM notifications", Long.class);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            consumer.consume(event);
            tx.setRollbackOnly();
        });
        assertThat(count("notification_receipts", event.eventId())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications", Long.class)).isEqualTo(before);
    }

    @Test
    @Transactional
    void duplicateDeliveryAndOccurrenceTime() {
        var event = event();
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM notifications", Long.class);
        consumer.consume(event);
        consumer.consume(event);
        assertThat(count("notification_receipts", event.eventId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications", Long.class)).isEqualTo(before + 1);
        var timestamp = jdbc.queryForObject("SELECT created_at FROM notifications ORDER BY id DESC LIMIT 1",
                java.sql.Timestamp.class);
        assertThat(timestamp.toInstant()).isEqualTo(event.occurredAt());
    }

    @Test
    @Transactional
    void expiredLeaseReclaimedAndOldOwnerCannotComplete() {
        var event = event();
        outbox.append(event);
        var first = outbox.claim().stream().filter(c -> c.event().eventId().equals(event.eventId())).findFirst().orElseThrow();
        jdbc.update("UPDATE notification_outbox SET locked_until=DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL 1 SECOND) WHERE event_id=?", event.eventId());
        var second = outbox.claim().stream().filter(c -> c.event().eventId().equals(event.eventId())).findFirst().orElseThrow();
        assertThat(second.leaseToken()).isNotEqualTo(first.leaseToken());
        outbox.sent(first);
        outbox.retry(first, "stale-owner");
        assertThat(jdbc.queryForObject("SELECT status FROM notification_outbox WHERE event_id=?", String.class,
                event.eventId())).isEqualTo("INFLIGHT");
        outbox.sent(second);
        assertThat(jdbc.queryForObject("SELECT status FROM notification_outbox WHERE event_id=?", String.class,
                event.eventId())).isEqualTo("SENT");
    }

    @Test
    @Transactional
    void pendingRetryNotDueCannotBeClaimed() {
        var event = event();
        outbox.append(event);
        var claim = outbox.claim().stream().filter(c -> c.event().eventId().equals(event.eventId())).findFirst().orElseThrow();
        outbox.retry(claim, "failure");
        jdbc.update("UPDATE notification_outbox SET next_attempt_at=DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL 1 HOUR) WHERE event_id=?", event.eventId());
        assertThat(outbox.claim()).noneMatch(c -> c.event().eventId().equals(event.eventId()));
        assertThat(outbox.backlog().pending()).isGreaterThanOrEqualTo(1);
    }

    private long count(String table, String id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE event_id=?", Long.class, id);
    }
}
