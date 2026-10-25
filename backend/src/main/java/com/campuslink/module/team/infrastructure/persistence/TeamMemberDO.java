package com.campuslink.module.team.infrastructure.persistence;

import lombok.Data;

import java.time.Instant;

/** team_members 表数据对象（CR-137）：联合主键，无单 id 字段 */
@Data
public class TeamMemberDO {

    private Long teamId;
    private Long userId;
    private String role;
    private String status;
    private Instant appliedAt;
    private Instant decidedAt;
}
