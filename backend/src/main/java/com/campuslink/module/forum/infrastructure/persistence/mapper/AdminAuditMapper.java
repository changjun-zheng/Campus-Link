package com.campuslink.module.forum.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campuslink.module.forum.infrastructure.persistence.AdminAuditRowDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 操作审计只读查询（CR-215）：{@code audit_logs} 左联 {@code users} 取操作者昵称。
 *
 * <p>与 CR-141 后台内容搜索同法：联 {@code users} 仅作展示，不参与跨上下文写路径；
 * 全部条件参数化，不做字符串拼接。{@code days} 由调用方保证只传 7 / 30 / 90。
 */
public interface AdminAuditMapper {

    @Select("""
            <script>
            SELECT a.id AS id, a.actor_id AS actorId, u.nickname AS actorNickname,
                   a.action AS action, a.target_type AS targetType, a.target_id AS targetId,
                   a.detail AS detail, a.created_at AS createdAt
            FROM audit_logs a
            LEFT JOIN users u ON u.id = a.actor_id
            WHERE 1 = 1
              <if test="action != null">AND a.action = #{action}</if>
              <if test="actorId != null">AND a.actor_id = #{actorId}</if>
              <if test="days != null">AND a.created_at &gt;= DATE_SUB(NOW(), INTERVAL #{days} DAY)</if>
            ORDER BY a.created_at DESC, a.id DESC
            </script>
            """)
    IPage<AdminAuditRowDO> page(IPage<AdminAuditRowDO> page,
                                @Param("action") String action,
                                @Param("actorId") Long actorId,
                                @Param("days") Integer days);
}
