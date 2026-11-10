package com.campuslink.module.account.web.vo;

import com.campuslink.module.account.application.cmd.AdminUserSearchResult;

import java.util.List;

/**
 * 后台用户检索结果视图（CR-211）：{@code truncated=true} 表示昵称匹配超过 50 条，
 * 前端须提示运营补充更精确的关键字，不能把本页当完整结果。
 */
public record AdminUserSearchVo(List<AdminUserItemVo> items, boolean truncated) {

    public AdminUserSearchVo {
        items = List.copyOf(items);
    }

    public static AdminUserSearchVo from(AdminUserSearchResult result) {
        return new AdminUserSearchVo(
                result.items().stream()
                        .map(item -> new AdminUserItemVo(item.id(), item.nickname(),
                                item.role().name(), item.status().name(), item.mutedUntil(),
                                item.createdAt()))
                        .toList(),
                result.truncated());
    }
}
