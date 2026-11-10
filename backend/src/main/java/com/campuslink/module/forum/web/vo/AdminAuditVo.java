package com.campuslink.module.forum.web.vo;

import com.campuslink.module.forum.application.AdminAuditApplicationService;

import java.time.Instant;

/** 操作审计条目（CR-215）：只读投影，detail 原样下发（列宽 2048，后台展示可截断） */
public record AdminAuditVo(long id, Long actorId, String actorNickname, String action, String targetType,
                           Long targetId, String detail, Instant createdAt) {

    public static AdminAuditVo from(AdminAuditApplicationService.AuditItem item) {
        return new AdminAuditVo(item.id(), item.actorId(), item.actorNickname(), item.action(),
                item.targetType(), item.targetId(), item.detail(), item.createdAt());
    }
}
