package com.campuslink.module.account.application.cmd;

import com.campuslink.module.account.domain.model.AccountRole;
import com.campuslink.module.account.domain.model.AccountStatus;

import java.time.Instant;
import java.util.List;

/**
 * 后台用户检索结果（CR-211）：只读投影，不含邮箱 / 手机号 / 学号及任何 {@code _enc} / {@code _hash}。
 *
 * <p>{@code truncated=true} 表示昵称匹配超过 50 条，本页只返回前 50 条；调用方据此提示运营
 * "请补充更精确的关键字"，而不是把截断当完整结果。
 */
public record AdminUserSearchResult(List<Item> items, boolean truncated) {

    public AdminUserSearchResult {
        items = List.copyOf(items);
    }

    public record Item(long id, String nickname, AccountRole role, AccountStatus status,
                       Instant mutedUntil, Instant createdAt) {
    }
}
