package com.campuslink.module.forum.infrastructure.persistence.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 运营看板聚合 SQL 的口径断言（F-ADMIN-005 / CR-131），读 {@code @Select} 注解文本
 * （与 CR-104 的注解字面量断言同法——Mockito 结构上捕不到 SQL 条件）。
 *
 * <p>三条口径：① 内容组排除作者自删（{@code is_deleted = 0}）；② 平台下架单列且计入 total；
 * ③ 今日用调用方传入的 {@code todayStart}（UTC 日界只在应用层产生），空表 SUM 由 COALESCE 兜底。
 */
class AdminStatsMapperSqlTest {

    @Test
    @DisplayName("帖子 / 楼层 SQL：只数未删、下架单列、今日参数化、空表 COALESCE")
    void contentSqlKeepsVisibilityAndTodayBoundary() throws NoSuchMethodException {
        String posts = selectSql("postStats", Instant.class);
        assertThat(posts)
                .contains("FROM posts")
                .contains("WHERE is_deleted = 0")
                .contains("status = 'PUBLISHED'")
                .contains("status = 'REMOVED'")
                .contains("created_at >= #{todayStart}")
                .contains("COALESCE");

        String replies = selectSql("replyStats", Instant.class);
        assertThat(replies)
                .contains("FROM replies")
                .contains("WHERE is_deleted = 0")
                .contains("created_at >= #{todayStart}");
    }

    @Test
    @DisplayName("举报 SQL：计数总 / 待处理 / 已闭环，空表 COALESCE")
    void reportSqlCountsAllStatuses() throws NoSuchMethodException {
        String sql = selectSql("reportStats");
        assertThat(sql)
                .contains("FROM reports")
                .contains("SUM(status = 'PENDING')")
                .contains("SUM(status = 'CLOSED')")
                .contains("COALESCE");
    }

    private static String selectSql(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        var method = AdminStatsMapper.class.getMethod(name, parameterTypes);
        return String.join("", method.getAnnotation(Select.class).value());
    }
}
