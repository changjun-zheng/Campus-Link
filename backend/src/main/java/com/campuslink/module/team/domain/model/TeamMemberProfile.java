package com.campuslink.module.team.domain.model;

/**
 * 组队详情页的成员条目（CR-137）：只含可公开露出的三列 + 角色。
 * 昵称在已注销账号上由 SQL 回落「已注销用户」，头像可空。
 */
public record TeamMemberProfile(long userId, String nickname, String avatarUrl, TeamMemberRole role) {
}
