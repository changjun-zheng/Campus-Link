package com.campuslink.module.account.web.vo;

import com.campuslink.module.account.application.cmd.BlockedUsers;

import java.util.List;

/** 黑名单分页视图（F-SOC-004 / CR-129） */
public record BlockedUsersVo(List<BlockedUserVo> items, long total, int page, int size) {

    public record BlockedUserVo(Long id, String nickname) {
    }

    public static BlockedUsersVo from(BlockedUsers result, int page, int size) {
        return new BlockedUsersVo(
                result.items().stream()
                        .map(item -> new BlockedUserVo(item.id(), item.nickname()))
                        .toList(),
                result.total(), page, size);
    }
}
