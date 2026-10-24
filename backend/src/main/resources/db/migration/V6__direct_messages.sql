-- ============================================================================
-- V6 · 私信（F-SOC-003 / CR-135）
--
-- 一人对一人只有一个会话：双方 id 以 (min, max) 归一落 pair_low_id / pair_high_id，
-- 唯一键 uk_conversations_pair 即"重复建会话天然幂等"（并发首条消息靠它兜底）。
-- conversations.last_message_id / last_message_at 是会话列表的排序与摘要来源，
-- 由发送用例在同一事务内回写；messages 只追加、不修改（本轮无撤回 / 编辑 / 删除）。
--
-- 回滚：V7__revert_cr135.sql（先 DROP messages，再 DROP conversations，见实施方案 §6）
-- ============================================================================

CREATE TABLE IF NOT EXISTS conversations (
    id              BIGINT   NOT NULL AUTO_INCREMENT,
    pair_low_id     BIGINT   NOT NULL COMMENT '双方中较小的 users.id',
    pair_high_id    BIGINT   NOT NULL COMMENT '双方中较大的 users.id',
    last_message_id BIGINT   NULL COMMENT '最后一条消息 messages.id',
    last_message_at DATETIME NULL COMMENT '最后一条消息时间（会话列表排序键）',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_conversations_pair (pair_low_id, pair_high_id),
    KEY idx_conversations_low (pair_low_id, last_message_at),
    KEY idx_conversations_high (pair_high_id, last_message_at),
    CONSTRAINT fk_conversations_low FOREIGN KEY (pair_low_id) REFERENCES users (id),
    CONSTRAINT fk_conversations_high FOREIGN KEY (pair_high_id) REFERENCES users (id)
) ENGINE = InnoDB COMMENT = '私信会话（CR-135）';

CREATE TABLE IF NOT EXISTS messages (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT       NOT NULL,
    sender_id       BIGINT       NOT NULL,
    receiver_id     BIGINT       NOT NULL,
    content         VARCHAR(1000) NOT NULL COMMENT '纯文本，不落 HTML',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at         DATETIME     NULL COMMENT '接收人读取时间；NULL 即未读',
    PRIMARY KEY (id),
    KEY idx_messages_conversation (conversation_id, created_at, id),
    KEY idx_messages_unread (receiver_id, read_at, conversation_id),
    CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id),
    CONSTRAINT fk_messages_sender FOREIGN KEY (sender_id) REFERENCES users (id),
    CONSTRAINT fk_messages_receiver FOREIGN KEY (receiver_id) REFERENCES users (id)
) ENGINE = InnoDB COMMENT = '私信消息（CR-135）';
