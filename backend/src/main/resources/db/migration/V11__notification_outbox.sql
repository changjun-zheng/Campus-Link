-- CR-193: durable notification intent and consumer deduplication. UTC session inherited from datasource.
CREATE TABLE notification_outbox (
    event_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    schema_version INT NOT NULL,
    type VARCHAR(32) NOT NULL,
    actor_id BIGINT NOT NULL,
    recipient_id BIGINT NOT NULL,
    target_type VARCHAR(16) NOT NULL,
    target_id BIGINT NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    lease_token VARCHAR(36) CHARACTER SET ascii NULL,
    locked_until DATETIME(6) NULL,
    sent_at DATETIME(6) NULL,
    last_error VARCHAR(128) NULL,
    INDEX idx_notification_outbox_pending (status, next_attempt_at),
    INDEX idx_notification_outbox_lease (status, locked_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE notification_receipts (
    event_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    consumed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
