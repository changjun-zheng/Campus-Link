package com.campuslink.module.forum.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 标签管理聚合 SQL 的口径断言（F-ADMIN-004 标签侧 / CR-132），读 {@code @Select} 注解文本
 * （Mockito 结构上捕不到 SQL 条件，与 CR-104 / CR-131 的注解字面量断言同法）。
 *
 * <p>三条口径：① 排序只在静态片段里分支（{@code use_count DESC} / {@code name ASC}），
 * 不出现 {@code ${}} 拼接；② 关键字走参数化 LIKE；③ 删除前能取到受影响帖子（清理冗余列的依据）。
 */
class AdminTagRepositorySqlTest {

    @Test
    @DisplayName("分页 SQL：静态排序分支 + 参数化 LIKE + 参数化分页，无 ${} 拼接")
    void pageTagsUsesStaticSortAndParameterizedKeyword() throws NoSuchMethodException {
        String sql = selectSql(TagMapper.class, "pageTags", String.class, String.class, int.class, int.class);
        assertThat(sql)
                .contains("FROM tags")
                .contains("LIKE CONCAT('%', #{keyword}, '%')")
                .contains("ORDER BY use_count DESC, id ASC")
                .contains("ORDER BY name ASC, id ASC")
                .contains("LIMIT #{limit} OFFSET #{offset}")
                .doesNotContain("${");
    }

    @Test
    @DisplayName("计数 SQL：与分页同一 WHERE（total 同源）")
    void countTagsSharesKeywordFilter() throws NoSuchMethodException {
        String sql = selectSql(TagMapper.class, "countTags", String.class);
        assertThat(sql)
                .contains("SELECT COUNT(*) FROM tags")
                .contains("LIKE CONCAT('%', #{keyword}, '%')");
    }

    @Test
    @DisplayName("受影响帖子查询：按 tag_id 取 post_id（删除前清理 posts.tags 的依据）")
    void postIdsQueryIsByTagId() throws NoSuchMethodException {
        String sql = selectSql(PostTagMapper.class, "findPostIdsByTagId", long.class);
        assertThat(sql).contains("SELECT post_id FROM post_tags WHERE tag_id = #{tagId}");
    }

    private static String selectSql(Class<?> mapper, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        return String.join("", mapper.getMethod(name, parameterTypes).getAnnotation(Select.class).value());
    }
}
