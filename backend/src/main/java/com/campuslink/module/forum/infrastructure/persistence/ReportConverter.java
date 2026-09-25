package com.campuslink.module.forum.infrastructure.persistence;

import com.campuslink.module.forum.domain.model.Report;

/** 防腐层：reports 表 DO ↔ 举报工单模型互转（CR-082） */
public final class ReportConverter {

    private ReportConverter() {
    }

    public static ReportDO toDo(Report r) {
        ReportDO d = new ReportDO();
        d.setId(r.id());
        d.setReporterId(r.reporterId());
        d.setTargetType(r.targetType());
        d.setTargetId(r.targetId());
        d.setReason(r.reason());
        d.setDetail(r.detail());
        d.setStatus(r.status());
        d.setResolution(r.resolution());
        d.setHandlerId(r.handlerId());
        d.setCreatedAt(r.createdAt());
        d.setClosedAt(r.closedAt());
        return d;
    }

    public static Report toDomain(ReportDO d) {
        return new Report(d.getId(), d.getReporterId(), d.getTargetType(), d.getTargetId(), d.getReason(),
                d.getDetail(), d.getStatus(), d.getResolution(), d.getHandlerId(), d.getCreatedAt(), d.getClosedAt());
    }
}
