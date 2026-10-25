package com.campuslink.module.team.infrastructure.persistence;

import lombok.Data;

import java.time.Instant;

/** 发起人待审批列表行（CR-137）：{@code team_members} + {@code users} 的只读投影 */
@Data
public class TeamApplicationDO {

    private Long userId;
    private String nickname;
    private String avatarUrl;
    private String status;
    private Instant appliedAt;
}
