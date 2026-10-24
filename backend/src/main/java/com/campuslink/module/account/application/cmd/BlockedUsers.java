package com.campuslink.module.account.application.cmd;

import java.util.List;

/** 黑名单分页结果（F-SOC-004 / CR-129）：id + 昵称，更新时间线不参与 */
public record BlockedUsers(List<BlockedUser> items, long total) {

    public record BlockedUser(Long id, String nickname) {
    }
}
