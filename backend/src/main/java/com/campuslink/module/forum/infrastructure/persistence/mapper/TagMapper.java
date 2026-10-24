package com.campuslink.module.forum.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campuslink.module.forum.infrastructure.persistence.AdminTagRow;
import com.campuslink.module.forum.infrastructure.persistence.TagDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface TagMapper extends BaseMapper<TagDO> {

    /**
     * 管理端标签分页（F-ADMIN-004 标签侧 / CR-132）。
     *
     * <p>排序只在**静态 SQL 片段**里分支（{@code <choose>}），不把 {@code sort} 拼进 ORDER BY；
     * {@code keyword} 为空时不加条件。{@code use_count} 是累计挂载次数（软删帖子不回退）。
     */
    @Select("""
            <script>
            SELECT id, name, use_count, created_at
            FROM tags
            <where>
              <if test="keyword != null and keyword != ''">name LIKE CONCAT('%', #{keyword}, '%')</if>
            </where>
            <choose>
              <when test="sort == 'name'">ORDER BY name ASC, id ASC</when>
              <otherwise>ORDER BY use_count DESC, id ASC</otherwise>
            </choose>
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<AdminTagRow> pageTags(@Param("keyword") String keyword,
                               @Param("sort") String sort,
                               @Param("offset") int offset,
                               @Param("limit") int limit);

    /** 与 {@link #pageTags} 同一 WHERE 的计数（total 与页内容同源） */
    @Select("""
            <script>
            SELECT COUNT(*) FROM tags
            <where>
              <if test="keyword != null and keyword != ''">name LIKE CONCAT('%', #{keyword}, '%')</if>
            </where>
            </script>
            """)
    long countTags(@Param("keyword") String keyword);

    @Select("SELECT id, name, use_count, created_at FROM tags WHERE id = #{tagId}")
    AdminTagRow findTag(@Param("tagId") long tagId);
}
