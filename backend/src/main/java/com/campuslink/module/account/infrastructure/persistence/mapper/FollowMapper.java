package com.campuslink.module.account.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campuslink.module.account.infrastructure.persistence.FollowDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface FollowMapper extends BaseMapper<FollowDO> {

    /**
     * 粉丝分页（CR-130）：条目 = 关注了 target 的账号（{@code f.follower_id}）。
     *
     * <p>过滤与分页在**同一条 SQL**里，{@link #countFollowers} 复用同一 WHERE，因此
     * {@code total} 与页内容必然同源（CR-129 的教训：应用层剔除会让两者不同源）：
     * ① 已注销账号剔除——列表条目点进主页会 2007，"列出来却点不开"是本仓明确避免的缺陷模式；
     * ② viewer 已拉黑账号剔除——与 CR-129 的读侧过滤口径一致。
     * {@code BANNED} 不剔除：封禁限的是发言与登录，公开资料仍可读（{@code publicAccountOf} 同口径）。
     *
     * <p>参数化 {@code LIMIT / OFFSET}，不拼字符串；次序带 id 作稳定第二键，同秒关注不抖动。
     */
    @Select("""
            SELECT f.follower_id
            FROM follows f
            JOIN users u ON u.id = f.follower_id
            WHERE f.followee_id = #{followeeId}
              AND u.status <> 'DEACTIVATED'
              AND NOT EXISTS (SELECT 1 FROM user_blocks ub
                              WHERE ub.blocker_id = #{viewerId} AND ub.blocked_id = f.follower_id)
            ORDER BY f.created_at DESC, f.follower_id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Long> pageFollowerIds(@Param("followeeId") long followeeId,
                               @Param("viewerId") long viewerId,
                               @Param("offset") int offset,
                               @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*)
            FROM follows f
            JOIN users u ON u.id = f.follower_id
            WHERE f.followee_id = #{followeeId}
              AND u.status <> 'DEACTIVATED'
              AND NOT EXISTS (SELECT 1 FROM user_blocks ub
                              WHERE ub.blocker_id = #{viewerId} AND ub.blocked_id = f.follower_id)
            """)
    long countFollowers(@Param("followeeId") long followeeId, @Param("viewerId") long viewerId);

    /** 关注分页（CR-130）：条目 = follower 关注的账号（{@code f.followee_id}），过滤口径与粉丝侧逐字相同 */
    @Select("""
            SELECT f.followee_id
            FROM follows f
            JOIN users u ON u.id = f.followee_id
            WHERE f.follower_id = #{followerId}
              AND u.status <> 'DEACTIVATED'
              AND NOT EXISTS (SELECT 1 FROM user_blocks ub
                              WHERE ub.blocker_id = #{viewerId} AND ub.blocked_id = f.followee_id)
            ORDER BY f.created_at DESC, f.followee_id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Long> pageFolloweeIds(@Param("followerId") long followerId,
                               @Param("viewerId") long viewerId,
                               @Param("offset") int offset,
                               @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*)
            FROM follows f
            JOIN users u ON u.id = f.followee_id
            WHERE f.follower_id = #{followerId}
              AND u.status <> 'DEACTIVATED'
              AND NOT EXISTS (SELECT 1 FROM user_blocks ub
                              WHERE ub.blocker_id = #{viewerId} AND ub.blocked_id = f.followee_id)
            """)
    long countFollowing(@Param("followerId") long followerId, @Param("viewerId") long viewerId);
}
