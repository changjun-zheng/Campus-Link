-- CR-209 用户警告与禁言：
-- 1) users.muted_until——禁言截止时间，NULL 表示未禁言；读取时按当前时间判定，过期自然失效，无需定时任务。
-- 2) notifications.message——平台通知正文（警告原因 / 禁言说明）；互动通知沿用读时组装，此列为 NULL。
ALTER TABLE users
    ADD COLUMN muted_until DATETIME NULL COMMENT '禁言截止时间（CR-209），NULL=未禁言' AFTER delete_at;

ALTER TABLE notifications
    ADD COLUMN message VARCHAR(255) NULL COMMENT '平台通知正文（CR-209：警告/禁言原因），互动通知为 NULL' AFTER target_id;
