package com.campuslink.module.forum.web.vo;

import com.campuslink.module.forum.application.AdminTagApplicationService;

import java.time.Instant;
import java.util.List;

/**
 * 管理端标签分页视图（F-ADMIN-004 标签侧 / CR-132）。
 *
 * <p>分页形状沿用 {@code BlockedUsersVo} / {@code FollowUsersVo} 的 {@code items / total / page / size}。
 * {@code useCount} 是**累计挂载次数**（软删帖子不回退），不是"当前可见帖子数"——前端文案须注明。
 */
public record AdminTagsVo(List<AdminTagVo> items, long total, int page, int size) {

    public record AdminTagVo(long id, String name, int useCount, Instant createdAt) {
    }

    public static AdminTagsVo from(AdminTagApplicationService.AdminTags result) {
        return new AdminTagsVo(
                result.items().stream()
                        .map(t -> new AdminTagVo(t.id(), t.name(), t.useCount(), t.createdAt()))
                        .toList(),
                result.total(), result.page(), result.size());
    }
}
