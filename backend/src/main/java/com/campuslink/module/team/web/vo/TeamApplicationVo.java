package com.campuslink.module.team.web.vo;

import com.campuslink.module.team.domain.model.TeamApplicationItem;

import java.time.Instant;

/** 发起人待审批条目（CR-137） */
public record TeamApplicationVo(long userId,
                                String nickname,
                                String avatarUrl,
                                String status,
                                Instant appliedAt) {

    public static TeamApplicationVo from(TeamApplicationItem item) {
        return new TeamApplicationVo(item.userId(), item.nickname(), item.avatarUrl(),
                item.status().name(), item.appliedAt());
    }
}
