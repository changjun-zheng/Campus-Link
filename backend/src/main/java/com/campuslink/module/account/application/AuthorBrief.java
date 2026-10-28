package com.campuslink.module.account.application;

/**
 * 跨上下文只读作者摘要（CR-162）：昵称与头像一次批量取回。
 *
 * <p>放在 account.application 是 ADR-012 的既定方向——forum / notification 只允许调对方
 * application 服务，不直接 join {@code users} 表；{@code avatarUrl} 可空（匿名帖由调用方置空、
 * 未回填的历史账号也可能为空，前端回落昵称首字）。
 */
public record AuthorBrief(String nickname, String avatarUrl) {
}
