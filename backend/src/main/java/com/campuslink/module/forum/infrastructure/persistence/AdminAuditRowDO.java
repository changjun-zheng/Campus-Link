package com.campuslink.module.forum.infrastructure.persistence;

import lombok.Data;

import java.time.Instant;

/**
 * 操作审计查询行（CR-215）：{@code audit_logs} 左联 {@code users} 的只读投影。
 * 列名经 {@code map-underscore-to-camel-case} 映射到同名属性。
 */
@Data
public class AdminAuditRowDO {

    private Long id;
    private Long actorId;
    private String actorNickname;
    private String action;
    private String targetType;
    private Long targetId;
    private String detail;
    private Instant createdAt;
}
