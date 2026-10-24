package com.campuslink.module.forum.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 搜索 SQL 的口径断言（F-FORUM-008 / F-FORUM-009 / CR-136）：读 {@code @Select} 注解文本
 * （Mockito 结构上捕不到 SQL 条件，与 CR-104 / CR-133 同法）。
 *
 * <p>三条口径：① 标题与正文各走一条 FULLTEXT（标题权重 2、正文权重 1），标签 LIKE 只作命中兜底；
 * ② 命中条件与相关度表达式使用同一组 MATCH，避免"能搜到但得分为 0"；③ 拉黑作者过滤、版块 / 时间窗
 * 与分页参数全部参数化，不拼字符串。
 */
class PostMapperSqlTest {

    @Test
    @DisplayName("搜索 SQL：标题 ×2 + 正文 ×1 双 MATCH，标签 LIKE 兜底")
    void searchSqlCoversTitleAndContent() throws NoSuchMethodException {
        String sql = selectSql("search");

        assertThat(sql)
                .contains("MATCH(title) AGAINST(#{keyword}) * 2")
                .contains("+ MATCH(content_md) AGAINST(#{keyword}) AS relevance")
                .contains("AND (MATCH(title) AGAINST(#{keyword}) OR MATCH(content_md) AGAINST(#{keyword})")
                .contains("OR MATCH(content_md) AGAINST(#{keyword})")
                .contains("OR tags LIKE CONCAT('%', #{keyword}, '%')")
                .contains("ORDER BY relevance DESC, created_at DESC, id DESC");
    }

    @Test
    @DisplayName("搜索 SQL：可见性、版块、时间窗、拉黑作者全部参数化，不拼字符串")
    void searchSqlKeepsFiltersParameterized() throws NoSuchMethodException {
        String sql = selectSql("search");

        assertThat(sql)
                .contains("status = 'PUBLISHED' AND is_deleted = 0")
                .contains("AND board_id = #{boardId}")
                .contains("DATE_SUB(NOW(), INTERVAL #{days} DAY)")
                .contains("author_id NOT IN")
                .contains("#{excludedId}")
                .doesNotContain("${");
    }

    private static String selectSql(String name) throws NoSuchMethodException {
        // 换行与缩进只是可读性，断言前归一为单空格，口径判断不受排版影响
        return String.join("", PostMapper.class.getMethod(name,
                        com.baomidou.mybatisplus.core.metadata.IPage.class, String.class, Long.class,
                        Integer.class, java.util.Collection.class).getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");
    }
}
