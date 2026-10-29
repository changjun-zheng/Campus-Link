package com.campuslink.module.account.application.cmd;

import java.util.List;

/**
 * 粉丝 / 关注分页结果（F-SOC-002 补齐 / CR-130）。
 *
 * <p>条目只含公开展示字段 {@code id / nickname / major / bio}：{@code email_* / phone_* / student_id_*}
 * 与 {@code role / status} 一律不下发（与 {@code UserProfileVo} 的"公开端点外泄面"同一条纪律）。
 *
 * <p>{@code total} 与页内容同源（SQL 同一 WHERE）：已注销账号与 viewer 已拉黑账号在查询侧就已剔除，
 * 因此它与 {@code follow-state} 的粉丝计数**口径不同**——后者是关系事实数、不过滤，前者是"可点开的条目数"。
 */
public record FollowUsers(List<FollowUser> items, long total, int page, int size) {

    public record FollowUser(Long id, String nickname, String avatarUrl, String major, String bio) {
    }
}
