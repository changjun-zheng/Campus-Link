package com.campuslink.module.forum.domain.model;

import java.time.Instant;

/** 举报工单（F-SAFE-002 / CR-082 / CR-214）：状态 PENDING → PROCESSING（认领）→ CLOSED；reason 取 PRD 枚举（ILLEGAL/HARASS/COPYRIGHT/SPAM/OTHER） */
public record Report(Long id, Long reporterId, String targetType, Long targetId, String reason,
                     String detail, String status, String resolution, Long handlerId,
                     Instant createdAt, Instant closedAt) {

    public static final String TARGET_POST = "POST";
    public static final String TARGET_REPLY = "REPLY";
    public static final String TARGET_USER = "USER";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_CLOSED = "CLOSED";

    public boolean isClosed() {
        return STATUS_CLOSED.equals(status);
    }

    /** 是否可认领（CR-214）：仅待处理工单 */
    public boolean isClaimable() {
        return STATUS_PENDING.equals(status);
    }
}
