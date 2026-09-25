package com.campuslink.module.forum.domain.model;

import java.time.Instant;

/** 举报工单（F-SAFE-002 / CR-082）：状态 PENDING → CLOSED；reason 取 PRD 枚举（ILLEGAL/HARASS/COPYRIGHT/SPAM/OTHER） */
public record Report(Long id, Long reporterId, String targetType, Long targetId, String reason,
                     String detail, String status, String resolution, Long handlerId,
                     Instant createdAt, Instant closedAt) {

    public static final String TARGET_POST = "POST";
    public static final String TARGET_REPLY = "REPLY";
    public static final String TARGET_USER = "USER";

    public boolean isClosed() {
        return "CLOSED".equals(status);
    }
}
