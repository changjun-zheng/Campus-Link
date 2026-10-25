-- ============================================================================
-- V8 · 组队广场（F-TEAM-001 / CR-137）
--
-- teams 是招募主体：发起人 + 方向 + 目标赛事 + 人数 + 截止 + 联系方式，
-- 联系方式只存 AES-GCM 密文（contact_enc），明文仅在仓储解密后交给应用层，
-- 且只对发起人与已加入成员下发（见方案 §7 D4）。
--
-- team_members 同时承载「发起人 / 申请 / 成员」三种状态：
--   role   = OWNER | MEMBER
--   status = PENDING | ACCEPTED | REJECTED
-- 联合主键 (team_id, user_id) 即「同一人对同一组队只有一条关系」，
-- 重复申请由它兜底（适配器把 DuplicateKey 翻成业务码 6004）。
--
-- 回滚：V9__revert_cr137.sql（先 DROP team_members，再 DROP teams，见实施方案 §6）
-- ============================================================================

CREATE TABLE IF NOT EXISTS teams (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    owner_id        BIGINT       NOT NULL COMMENT '发起人 users.id',
    title           VARCHAR(60)  NOT NULL COMMENT '招募标题',
    description_md  VARCHAR(2000) NOT NULL COMMENT 'Markdown 原文，读取时经 MarkdownRenderer 渲染',
    direction       VARCHAR(16)  NOT NULL COMMENT 'ALGORITHM / FRONTEND / BACKEND / MOBILE / DATA / SECURITY / OTHER',
    target          VARCHAR(64)  NULL COMMENT '目标赛事 / 项目名称（选填）',
    capacity        TINYINT      NOT NULL COMMENT '总人数（含发起人），2~20',
    contact_enc     VARCHAR(512) NOT NULL COMMENT '联系方式密文（AES-GCM，仅成员可见）',
    status          VARCHAR(16)  NOT NULL DEFAULT 'RECRUITING' COMMENT 'RECRUITING / FULL / CLOSED',
    deadline        DATE         NULL COMMENT '招募截止日，仅展示，不自动改状态',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_teams_status (status, created_at, id),
    KEY idx_teams_owner (owner_id, created_at),
    CONSTRAINT fk_teams_owner FOREIGN KEY (owner_id) REFERENCES users (id)
) ENGINE = InnoDB COMMENT = '组队招募（CR-137）';

CREATE TABLE IF NOT EXISTS team_members (
    team_id     BIGINT   NOT NULL,
    user_id     BIGINT   NOT NULL,
    role        VARCHAR(16) NOT NULL COMMENT 'OWNER / MEMBER',
    status      VARCHAR(16) NOT NULL COMMENT 'PENDING / ACCEPTED / REJECTED',
    applied_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_at  DATETIME NULL COMMENT '审批时间；PENDING 时为 NULL',
    PRIMARY KEY (team_id, user_id),
    KEY idx_tm_user (user_id, status, applied_at),
    KEY idx_tm_team_status (team_id, status, applied_at),
    CONSTRAINT fk_tm_team FOREIGN KEY (team_id) REFERENCES teams (id),
    CONSTRAINT fk_tm_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB COMMENT = '组队成员与申请（CR-137）';
