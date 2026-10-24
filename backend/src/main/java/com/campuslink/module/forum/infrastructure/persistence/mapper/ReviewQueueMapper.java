package com.campuslink.module.forum.infrastructure.persistence.mapper;

import com.campuslink.module.forum.infrastructure.persistence.ReviewItemRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 人工巡查队列查询（F-SAFE-004 / CR-133）。四条 SQL 全为静态文本：
 * 排序与分页参数化，不做字符串拼接。
 *
 * <p>口径：只列未删内容；楼层额外排除父帖已删（父帖不可访问，处置无意义）；
 * 楼层摘要用 {@code LEFT(content_md, 60)} 取纯文本前 60 字，**不取 content_html**。
 */
public interface ReviewQueueMapper {

    @Select("""
            SELECT p.id AS id, 'post' AS kind, p.title AS title, b.name AS boardName,
                   u.nickname AS authorNickname, p.status AS status, p.created_at AS createdAt
            FROM posts p
            JOIN users u ON u.id = p.author_id
            JOIN boards b ON b.id = p.board_id
            WHERE p.is_deleted = 0
            ORDER BY p.created_at DESC, p.id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<ReviewItemRow> posts(@Param("offset") int offset, @Param("limit") int limit);

    @Select("""
            SELECT r.id AS id, 'reply' AS kind, LEFT(r.content_md, 60) AS title, b.name AS boardName,
                   u.nickname AS authorNickname, r.status AS status, r.created_at AS createdAt
            FROM replies r
            JOIN posts p ON p.id = r.post_id
            JOIN users u ON u.id = r.author_id
            JOIN boards b ON b.id = p.board_id
            WHERE r.is_deleted = 0 AND p.is_deleted = 0
            ORDER BY r.created_at DESC, r.id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<ReviewItemRow> replies(@Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM posts WHERE is_deleted = 0")
    long countPosts();

    @Select("""
            SELECT COUNT(*)
            FROM replies r
            JOIN posts p ON p.id = r.post_id
            WHERE r.is_deleted = 0 AND p.is_deleted = 0
            """)
    long countReplies();
}
