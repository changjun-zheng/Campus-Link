package com.campuslink.module.forum.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campuslink.module.forum.infrastructure.persistence.PostDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** posts 表 Mapper（包名约束见 {@link BoardMapper}） */
public interface PostMapper extends BaseMapper<PostDO> {

    /**
     * 站内搜索（F-FORUM-008）：标题走 V1 已就位的 {@code FULLTEXT ft_posts_title}（ngram 分词，中文 2 字起可命中），
     * 标签走 {@code posts.tags} 冗余列 LIKE 兜底（该列当前无数据，有值即生效）；相关度得分随 MATCH 产生，
     * 排序 {@code 得分 DESC, created_at DESC, id DESC}。{@code keyword} 由应用层校验长度，此处只参数化传入。
     */
    @Select("""
            <script>
            SELECT *, MATCH(title) AGAINST(#{keyword}) AS relevance
            FROM posts
            WHERE status = 'PUBLISHED' AND is_deleted = 0
              AND (MATCH(title) AGAINST(#{keyword}) OR tags LIKE CONCAT('%', #{keyword}, '%'))
              <if test="boardId != null">AND board_id = #{boardId}</if>
              <if test="days != null">AND created_at &gt;= DATE_SUB(NOW(), INTERVAL #{days} DAY)</if>
            ORDER BY relevance DESC, created_at DESC, id DESC
            </script>
            """)
    IPage<PostDO> search(IPage<PostDO> page,
                         @Param("keyword") String keyword,
                         @Param("boardId") Long boardId,
                         @Param("days") Integer days);

    /**
     * Similar-post recommendation (publish-time assist): full-text search on title (ngram),
     * restricted to PUBLISHED + not-deleted posts in the given board, excluding the caller's own posts.
     * Results ordered by relevance DESC, created_at DESC, id DESC, capped at {@code limit}.
     *
     * <p>显式列清单（CR-077 Warning #7）：只取推荐条目需要的 6 列，避免 SELECT * 拉出 content_md / content_html 等大字段。
     */
    @Select("""
            SELECT id, board_id, title, reply_count, is_accepted, created_at,
                   MATCH(title) AGAINST(#{keyword} IN NATURAL LANGUAGE MODE) AS relevance
            FROM posts
            WHERE status = 'PUBLISHED' AND is_deleted = 0
              AND board_id = #{boardId}
              AND author_id != #{excludeAuthorId}
              AND MATCH(title) AGAINST(#{keyword} IN NATURAL LANGUAGE MODE)
            ORDER BY relevance DESC, created_at DESC, id DESC
            LIMIT #{limit}
            """)
    List<PostDO> findSimilar(@Param("keyword") String keyword,
                             @Param("boardId") Long boardId,
                             @Param("excludeAuthorId") Long excludeAuthorId,
                             @Param("limit") int limit);

    /**
     * 作者可见帖子的获赞总数（他人主页资料卡「获赞 N」，CR-104）。
     *
     * <p>WHERE 与 {@code PostRepositoryImpl#countVisibleByAuthor} / {@code #findPageByAuthors} 的
     * {@code visibleQuery()} 三个条件逐字相同——这里是 SQL 字面量、那边是 Lambda 构造器，
     * **两处各写一遍是本条已知代价**（抽成共享 SQL 常量会把条件同时钉进 domain 与 mapper，更糊）。
     * 同源性由 {@code ForumQueryApplicationServiceAuthorStatsTest} 的「下架帖的赞不计入」用例守着。
     *
     * <p>读冗余计数列 {@code like_count}（由 {@code adjustLikeCount} 单点增减）而非 JOIN {@code likes} 现算：
     * 现算要按作者扫全部点赞行，而主页是一次高频读、量级不对称。
     */
    @Select("""
            SELECT COALESCE(SUM(like_count), 0)
            FROM posts
            WHERE author_id = #{authorId}
              AND status = 'PUBLISHED'
              AND is_deleted = 0
            """)
    long sumVisibleLikeCountByAuthor(@Param("authorId") Long authorId);
}
