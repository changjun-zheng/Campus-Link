package com.campuslink.module.team.web.vo;

import com.campuslink.module.team.domain.model.MyTeamItem;

import java.time.Instant;
import java.time.LocalDate;

/** 「我的组队」条目（CR-137）：owner 视角带 pendingCount，member 视角带申请状态 */
public record MyTeamVo(long id,
                       String title,
                       String direction,
                       String directionLabel,
                       String target,
                       int capacity,
                       long acceptedCount,
                       String status,
                       LocalDate deadline,
                       String myRole,
                       String myApplicationStatus,
                       long pendingCount,
                       Instant createdAt) {

    public static MyTeamVo from(MyTeamItem item) {
        return new MyTeamVo(item.id(), item.title(), item.direction().name(), item.direction().getLabel(),
                item.target(), item.capacity(), item.acceptedCount(), item.status().name(), item.deadline(),
                item.myRole().name(), item.myStatus().name(), item.pendingCount(), item.createdAt());
    }
}
