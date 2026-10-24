package com.campuslink.module.account.web.vo;

import com.campuslink.module.account.application.cmd.FollowUsers;

import java.util.List;

/**
 * 粉丝 / 关注列表分页视图（F-SOC-002 补齐 / CR-130）。
 *
 * <p>分页形状沿用 {@link BlockedUsersVo} 的 {@code items / total / page / size}，不复用 forum 与
 * notification 各自的 {@code PageVo}——那两个类型分属各自上下文，account 跨上下文引用它们会破坏
 * ADR-012 的依赖方向（守护测试 G4）。
 *
 * <p>{@code total} 是**可点开的条目数**（已注销与 viewer 已拉黑账号已在 SQL 侧剔除），
 * 与 {@code follow-state} 的 {@code fanCount / followCount}（关系事实数）口径不同，前端不要互相替代。
 */
public record FollowUsersVo(List<FollowUserVo> items, long total, int page, int size) {

    public record FollowUserVo(Long id, String nickname, String major, String bio) {
    }

    public static FollowUsersVo from(FollowUsers result) {
        return new FollowUsersVo(
                result.items().stream()
                        .map(item -> new FollowUserVo(item.id(), item.nickname(), item.major(), item.bio()))
                        .toList(),
                result.total(), result.page(), result.size());
    }
}
