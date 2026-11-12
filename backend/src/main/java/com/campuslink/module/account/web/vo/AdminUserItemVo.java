package com.campuslink.module.account.web.vo;

import java.time.Instant;

/**
 * 后台用户检索单条结果（CR-211）：只暴露运营处置所需的账号基础状态。
 *
 * <p>刻意不含邮箱 / 手机号 / 学号，也不含 {@code _enc} / {@code _hash}；角色与状态用枚举名字符串，
 * 与既有后台 VO 的契约形态一致。
 */
public record AdminUserItemVo(long id, String nickname, String role, String status,
                              Instant mutedUntil, Instant createdAt) {
}
