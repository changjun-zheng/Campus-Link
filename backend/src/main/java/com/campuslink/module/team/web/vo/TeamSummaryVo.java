package com.campuslink.module.team.web.vo;

import com.campuslink.module.team.domain.model.TeamListItem;

import java.time.Instant;
import java.time.LocalDate;

/** 组队广场列表条目（CR-137）：不含简介与联系方式，字段与前端 `api/teams.ts` 一一对应。 */
public record TeamSummaryVo(long id,
                            String title,
                            String direction,
                            String directionLabel,
                            String target,
                            int capacity,
                            long acceptedCount,
                            String status,
                            LocalDate deadline,
                            long ownerId,
                            String ownerNickname,
                            Instant createdAt) {

    public static TeamSummaryVo from(TeamListItem item) {
        return new TeamSummaryVo(item.id(), item.title(), item.direction().name(), item.direction().getLabel(),
                item.target(), item.capacity(), item.acceptedCount(), item.status().name(), item.deadline(),
                item.ownerId(), item.ownerNickname(), item.createdAt());
    }
}
