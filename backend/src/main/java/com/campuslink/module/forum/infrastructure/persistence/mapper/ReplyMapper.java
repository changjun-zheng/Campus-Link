package com.campuslink.module.forum.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campuslink.module.forum.infrastructure.persistence.MyReplyDO;
import com.campuslink.module.forum.infrastructure.persistence.PublicReplyDO;
import com.campuslink.module.forum.infrastructure.persistence.ReplyDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** replies 表 Mapper（包名约束见 {@link BoardMapper}） */
public interface ReplyMapper extends BaseMapper<ReplyDO> {

    /**
     * "我的回帖"分页（F-ACC-007c）：楼层与**父帖的可见性在同一条 SQL 里判定**，
     * 父帖已自删（{@code p.is_deleted=1}）或被平台下架（{@code p.status<>'PUBLISHED'}）时整条不出现——
     * 与 {@code ForumQueryApplicationService#requireVisiblePost} 及 CR-066 收口的通知侧同源
     * （"标题回落了、链接还可点"是本仓抓过两次的缺陷模式，此处不能再犯第三次）。
     *
     * <p>为什么不用两次查询在内存里拼：那样 {@code total} 是楼层侧的数、条目却是过滤后的，
     * 二者必然不同源（端口 javadoc 的"total 与查询条件同源"判据）。分页交给 MyBatis-Plus 的
     * {@code IPage} + 分页插件，写法照 {@code PostMapper#search} 先例。
     *
     * <p>楼层**自身**的 {@code status} 不过滤、随列一起返回：作者要能看到"我哪一层被下架了"（可逆性判据）。
     */
    @Select("""
            SELECT r.id, r.post_id, r.floor_no, r.content_md, r.status, r.created_at,
                   p.title AS post_title
            FROM replies r
            JOIN posts p ON p.id = r.post_id
            WHERE r.author_id = #{authorId}
              AND r.is_deleted = 0
              AND p.is_deleted = 0
              AND p.status = 'PUBLISHED'
            ORDER BY r.created_at DESC, r.id DESC
            """)
    IPage<MyReplyDO> findPageByAuthor(IPage<MyReplyDO> page, @Param("authorId") Long authorId);

    /**
     * 他人主页的公开楼层分页（路人视角，CR-104）：与上面那条本人视角的**唯一**差别是
     * {@code r.status = 'PUBLISHED'} 这一道——被平台下架的楼层对路人不得出现，对作者本人要出现（可逆性判据）。
     *
     * <p>父帖侧两个条件逐字相同（未删 + PUBLISHED），父帖标题仍与判定在**同一条 SQL** 里取，
     * 于是"主页列了楼层、点进去帖已下架"结构上不可能发生。
     *
     * <p>不取 {@code r.status} 列：见 {@link PublicReplyDO} javadoc。分页交给 {@code IPage} + 分页插件，
     * {@code total} 与本 WHERE 同源。
     */
    @Select("""
            SELECT r.id, r.post_id, r.floor_no, r.content_md, r.created_at,
                   p.title AS post_title
            FROM replies r
            JOIN posts p ON p.id = r.post_id
            WHERE r.author_id = #{authorId}
              AND r.is_deleted = 0
              AND r.status = 'PUBLISHED'
              AND p.is_deleted = 0
              AND p.status = 'PUBLISHED'
            ORDER BY r.created_at DESC, r.id DESC
            """)
    IPage<PublicReplyDO> findPublicPageByAuthor(IPage<PublicReplyDO> page, @Param("authorId") Long authorId);

    /**
     * 作者可见楼层的获赞总数（获赞数 D-1 的第二半，CR-104）：JOIN 父帖是必要的——
     * 一条被下架的回答继续替作者挣着对外展示的赞，等于让已处置内容仍在产出信号。
     * 条件与 {@link #findPublicPageByAuthor} 的 WHERE 逐字相同。
     */
    @Select("""
            SELECT COALESCE(SUM(r.like_count), 0)
            FROM replies r
            JOIN posts p ON p.id = r.post_id
            WHERE r.author_id = #{authorId}
              AND r.is_deleted = 0
              AND r.status = 'PUBLISHED'
              AND p.is_deleted = 0
              AND p.status = 'PUBLISHED'
            """)
    long sumVisibleLikeCountByAuthor(@Param("authorId") Long authorId);

    /**
     * 作者被采纳为最佳答案的可见楼层数（被采纳数 D-2 回答侧，CR-104）。
     *
     * <p>{@code is_accepted} 由 {@code updateAcceptedFlags} 单点维护（换采纳时旧位清零），与详情页
     * 「已采纳」标识同源；WHERE 在可见性三条件上再加 {@code r.is_accepted = 1}。
     */
    @Select("""
            SELECT COUNT(*)
            FROM replies r
            JOIN posts p ON p.id = r.post_id
            WHERE r.author_id = #{authorId}
              AND r.is_accepted = 1
              AND r.is_deleted = 0
              AND r.status = 'PUBLISHED'
              AND p.is_deleted = 0
              AND p.status = 'PUBLISHED'
            """)
    long countAcceptedVisibleByAuthor(@Param("authorId") Long authorId);
}
