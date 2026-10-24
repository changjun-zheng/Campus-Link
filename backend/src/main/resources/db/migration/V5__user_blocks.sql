-- ============================================================================
-- V5 · 用户拉黑 / 屏蔽（F-SOC-004 / CR-129）
--
-- 单向关系：blocker 不再看到 blocked 的内容、互动与通知（行为口径见实施方案 §2）。
-- 联合主键即"一人对一人只有一行"，重复拉黑天然幂等；idx_blocked 支撑"谁拉黑了谁"的反向查询
-- （本期未消费，留给后续管理端 / 粉丝类读路径）。
-- ============================================================================

CREATE TABLE IF NOT EXISTS user_blocks (
    blocker_id BIGINT NOT NULL COMMENT '拉黑发起人（users.id）',
    blocked_id BIGINT NOT NULL COMMENT '被拉黑人（users.id）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (blocker_id, blocked_id),
    KEY idx_blocked (blocked_id),
    CONSTRAINT fk_user_blocks_blocker FOREIGN KEY (blocker_id) REFERENCES users (id),
    CONSTRAINT fk_user_blocks_blocked FOREIGN KEY (blocked_id) REFERENCES users (id)
) ENGINE = InnoDB COMMENT = '用户拉黑关系（CR-129）';
