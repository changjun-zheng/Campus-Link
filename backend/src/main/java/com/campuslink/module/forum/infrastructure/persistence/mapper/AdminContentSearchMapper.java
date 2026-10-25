package com.campuslink.module.forum.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campuslink.module.forum.infrastructure.persistence.AdminContentRowDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;

/**
 * 后台内容搜索（F-ADMIN-002 / CR-141）：帖子 / 楼层两条分页查询。
 *
 * <p>条件全部参数化：关键词 LIKE、作者 id 集合、时间窗、分页都走占位符，不做字符串拼接。
 * 只列未删内容；{@code status} 不过滤（已下架仍可搜到并恢复）。楼层联父帖取标题并排除父帖已删，
 * 作者昵称在此联 {@code users}（与 CR-133 巡查队列同法，仅展示用，不参与跨上下文写路径）。
 */
public interface AdminContentSearchMapper {

    @Select("""
            <script>
            SELECT p.id AS id, 'post' AS kind, p.title AS title, p.content_md AS contentMd,
                   b.name AS boardName, p.author_id AS authorId, u.nickname AS authorNickname,
                   p.status AS status, p.created_at AS createdAt
            FROM posts p
            JOIN users u ON u.id = p.author_id
            JOIN boards b ON b.id = p.board_id
            WHERE p.is_deleted = 0
              <if test="keyword != null and keyword != ''">
                AND (p.title LIKE CONCAT('%', #{keyword}, '%')
                     OR p.content_md LIKE CONCAT('%', #{keyword}, '%'))
              </if>
              <if test="authorIds != null and authorIds.size() > 0">
                AND p.author_id IN
                <foreach collection="authorIds" item="authorId" open="(" separator="," close=")">
                  #{authorId}
                </foreach>
              </if>
              <if test="days != null">AND p.created_at &gt;= DATE_SUB(NOW(), INTERVAL #{days} DAY)</if>
            ORDER BY p.created_at DESC, p.id DESC
            </script>
            """)
    IPage<AdminContentRowDO> searchPosts(IPage<AdminContentRowDO> page,
                                         @Param("keyword") String keyword,
                                         @Param("authorIds") Collection<Long> authorIds,
                                         @Param("days") Integer days);

    @Select("""
            <script>
            SELECT r.id AS id, 'reply' AS kind, p.title AS title, r.content_md AS contentMd,
                   b.name AS boardName, r.author_id AS authorId, u.nickname AS authorNickname,
                   r.status AS status, r.created_at AS createdAt
            FROM replies r
            JOIN posts p ON p.id = r.post_id
            JOIN users u ON u.id = r.author_id
            JOIN boards b ON b.id = p.board_id
            WHERE r.is_deleted = 0 AND p.is_deleted = 0
              <if test="keyword != null and keyword != ''">
                AND (p.title LIKE CONCAT('%', #{keyword}, '%')
                     OR r.content_md LIKE CONCAT('%', #{keyword}, '%'))
              </if>
              <if test="authorIds != null and authorIds.size() > 0">
                AND r.author_id IN
                <foreach collection="authorIds" item="authorId" open="(" separator="," close=")">
                  #{authorId}
                </foreach>
              </if>
              <if test="days != null">AND r.created_at &gt;= DATE_SUB(NOW(), INTERVAL #{days} DAY)</if>
            ORDER BY r.created_at DESC, r.id DESC
            </script>
            """)
    IPage<AdminContentRowDO> searchReplies(IPage<AdminContentRowDO> page,
                                           @Param("keyword") String keyword,
                                           @Param("authorIds") Collection<Long> authorIds,
                                           @Param("days") Integer days);
}
