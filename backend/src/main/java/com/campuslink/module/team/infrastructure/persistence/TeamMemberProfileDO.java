package com.campuslink.module.team.infrastructure.persistence;

import lombok.Data;

/** 详情页成员联表查询行（CR-137）：{@code team_members} + {@code users} 的只读投影 */
@Data
public class TeamMemberProfileDO {

    private Long userId;
    private String nickname;
    private String avatarUrl;
    private String role;
}
