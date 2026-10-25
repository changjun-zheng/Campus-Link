package com.campuslink.module.forum.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 后台内容搜索 SQL 的口径断言（F-ADMIN-002 / CR-141）：读 {@code @Select} 注解文本
 * （Mockito 结构上捕不到 SQL 条件，与 CR-133 同法）。
 *
 * <p>两条硬口径：只列未删内容、{@code status} 不过滤（已下架仍可搜到并恢复）；
 * 关键词 / 作者集合 / 时间窗全部参数化，不拼字符串。
 */
class AdminContentSearchMapperSqlTest {

    @Test
    @DisplayName("帖子搜索 SQL：未删可见性、关键词 / 作者 / 时间窗参数化，status 不过滤")
    void searchPostsSqlKeepsFiltersParameterized() throws NoSuchMethodException {
        String sql = selectSql("searchPosts");

        assertThat(sql)
                .contains("WHERE p.is_deleted = 0")
                .contains("p.title LIKE CONCAT('%', #{keyword}, '%')")
                .contains("p.content_md LIKE CONCAT('%', #{keyword}, '%')")
                .contains("AND p.author_id IN")
                .contains("#{authorId}")
                .contains("AND p.created_at &gt;= DATE_SUB(NOW(), INTERVAL #{days} DAY)")
                .doesNotContain("p.status =")
                .doesNotContain("${");
    }

    @Test
    @DisplayName("楼层搜索 SQL：楼层与父帖双未删、联父帖取标题，条件同样参数化")
    void searchRepliesSqlKeepsFiltersParameterized() throws NoSuchMethodException {
        String sql = selectSql("searchReplies");

        assertThat(sql)
                .contains("WHERE r.is_deleted = 0 AND p.is_deleted = 0")
                .contains("p.title AS title")
                .contains("r.content_md LIKE CONCAT('%', #{keyword}, '%')")
                .contains("AND r.author_id IN")
                .contains("#{authorId}")
                .contains("AND r.created_at &gt;= DATE_SUB(NOW(), INTERVAL #{days} DAY)")
                .doesNotContain("r.status =")
                .doesNotContain("${");
    }

    private static String selectSql(String name) throws NoSuchMethodException {
        return String.join("", AdminContentSearchMapper.class.getMethod(name,
                        com.baomidou.mybatisplus.core.metadata.IPage.class, String.class,
                        java.util.Collection.class, Integer.class)
                .getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");
    }
}
