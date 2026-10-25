package com.campuslink.module.team.web.vo;

import com.campuslink.module.team.domain.model.TeamMemberProfile;

/** 详情页成员条目（CR-137）：昵称 / 头像 / 角色；只含 ACCEPTED 成员 */
public record TeamMemberVo(long userId, String nickname, String avatarUrl, String role) {

    public static TeamMemberVo from(TeamMemberProfile profile) {
        return new TeamMemberVo(profile.userId(), profile.nickname(), profile.avatarUrl(), profile.role().name());
    }
}
