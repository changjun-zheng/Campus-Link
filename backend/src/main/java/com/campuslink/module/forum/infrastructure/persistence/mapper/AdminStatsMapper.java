package com.campuslink.module.forum.infrastructure.persistence.mapper;

import com.campuslink.module.forum.infrastructure.persistence.ContentStatsRow;
import com.campuslink.module.forum.infrastructure.persistence.ReportStatsRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;

/**
 * 运营看板聚合查询（F-ADMIN-005 / CR-131）。三条 SQL 各返回一行聚合结果：
 * 不做逐行扫描、不在应用层数数（后者在数据量上来后会拖垮看板）。
 *
 * <p>口径写在 SQL 里且只有一处：{@code WHERE is_deleted = 0} 排除作者自删；
 * {@code status = 'REMOVED'} 计入 total 并单列；{@code todayCreated} 用调用方传入的
 * UTC 日界（{@code created_at} 存的是 UTC 墙钟）。
 */
public interface AdminStatsMapper {

    @Select("""
            SELECT COUNT(*) AS total,
                   COALESCE(SUM(status = 'PUBLISHED'), 0) AS published,
                   COALESCE(SUM(status = 'REMOVED'), 0) AS removed,
                   COALESCE(SUM(created_at >= #{todayStart}), 0) AS todayCreated
            FROM posts
            WHERE is_deleted = 0
            """)
    ContentStatsRow postStats(@Param("todayStart") Instant todayStart);

    @Select("""
            SELECT COUNT(*) AS total,
                   COALESCE(SUM(status = 'PUBLISHED'), 0) AS published,
                   COALESCE(SUM(status = 'REMOVED'), 0) AS removed,
                   COALESCE(SUM(created_at >= #{todayStart}), 0) AS todayCreated
            FROM replies
            WHERE is_deleted = 0
            """)
    ContentStatsRow replyStats(@Param("todayStart") Instant todayStart);

    @Select("""
            SELECT COUNT(*) AS total,
                   COALESCE(SUM(status = 'PENDING'), 0) AS pending,
                   COALESCE(SUM(status = 'CLOSED'), 0) AS closed
            FROM reports
            """)
    ReportStatsRow reportStats();
}
