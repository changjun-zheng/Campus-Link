package com.campuslink.module.forum.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 巡查队列 SQL 的口径断言（F-SAFE-004 / CR-133），读 {@code @Select} 注解文本
 * （Mockito 结构上捕不到 SQL 条件，与 CR-104 / CR-131 / CR-132 同法）。
 *
 * <p>四条口径：① 只列未删内容；② 楼层排除父帖已删；③ 楼层摘要取纯文本前 60 字、**不取 HTML**；
 * ④ 排序与分页全参数化、无 {@code ${}} 拼接。
 */
class ReviewQueueMapperSqlTest {

    @Test
    @DisplayName("帖子 SQL：只数未删、时间倒序、分页参数化")
    void postsSqlIsStaticAndOrdered() throws NoSuchMethodException {
        String sql = selectSql("posts", int.class, int.class);
        assertThat(sql)
                .contains("FROM posts p")
                .contains("WHERE p.is_deleted = 0")
                .contains("ORDER BY p.created_at DESC, p.id DESC")
                .contains("LIMIT #{limit} OFFSET #{offset}")
                .doesNotContain("${");
    }

    @Test
    @DisplayName("楼层 SQL：排除父帖已删、摘要取 content_md 前 60 字、不取 content_html")
    void repliesSqlUsesPlainTextSummaryAndParentFilter() throws NoSuchMethodException {
        String sql = selectSql("replies", int.class, int.class);
        assertThat(sql)
                .contains("FROM replies r")
                .contains("JOIN posts p ON p.id = r.post_id")
                .contains("WHERE r.is_deleted = 0 AND p.is_deleted = 0")
                .contains("LEFT(r.content_md, 60)")
                .contains("ORDER BY r.created_at DESC, r.id DESC")
                .doesNotContain("content_html")
                .doesNotContain("${");
    }

    @Test
    @DisplayName("计数 SQL：与分页同一 WHERE（total 同源）")
    void countQueriesShareVisibility() throws NoSuchMethodException {
        assertThat(selectSql("countPosts")).contains("WHERE is_deleted = 0");
        assertThat(selectSql("countReplies"))
                .contains("WHERE r.is_deleted = 0 AND p.is_deleted = 0");
    }

    private static String selectSql(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        return String.join("", ReviewQueueMapper.class.getMethod(name, parameterTypes)
                .getAnnotation(Select.class).value());
    }
}
