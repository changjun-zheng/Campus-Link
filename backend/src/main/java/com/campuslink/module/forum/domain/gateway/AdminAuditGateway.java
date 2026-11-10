package com.campuslink.module.forum.domain.gateway;

import java.time.Instant;

/**
 * 操作审计只读查询端口（CR-215）：面向运营后台的 {@code audit_logs} 检索。
 *
 * <p>本端口**只读**——审计写入仍是 {@code common.audit.AuditService} 的唯一职责，
 * 适配器不提供 insert / update / delete，避免出现第二条写路径。
 *
 * <p>筛选口径：{@code action} 为 null 不筛；{@code actorId} 为 null 不筛；
 * {@code days} 为 null 不设时间窗（应用层已保证只接受 7 / 30 / 90）。
 * 结果按 {@code created_at DESC, id DESC} 倒序——同一秒内多条时用 id 保持稳定顺序。
 */
public interface AdminAuditGateway {

    PageResult<AuditRow> page(String action, Long actorId, Integer days, int page, int size);

    /**
     * 审计行（窄载体）：{@code actorNickname} 由适配器 {@code LEFT JOIN users} 取展示用昵称；
     * 操作者不存在时为 null，由应用层按「系统 / 已注销用户」兜底——本端口不承载展示文案。
     */
    record AuditRow(long id, Long actorId, String actorNickname, String action, String targetType,
                    Long targetId, String detail, Instant createdAt) {
    }
}
