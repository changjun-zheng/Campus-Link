package com.campuslink.module.account.domain.gateway;

import java.util.List;

/**
 * 出站端口：关注关系（CR-074）。幂等语义在实现侧保证（重复 follow / unfollow 不报错不重复落行）；
 * 关注是用户关系事实，归 account 上下文；feed 消费方（forum）只经本上下文 application 拿 id 列表。
 */
public interface FollowRepository {

    /** 关注（重复关注幂等：唯一键冲突即忽略，不抛错） */
    void follow(Long followerId, Long followeeId);

    /** 取消关注（未关注时幂等，删 0 行不报错） */
    void unfollow(Long followerId, Long followeeId);

    boolean existsByFollowerAndFollowee(Long followerId, Long followeeId);

    /** 粉丝数：有多少人关注了 followee */
    long countByFollowee(Long followeeId);

    /** 关注数：follower 关注了多少人 */
    long countByFollower(Long followerId);

    /** follower 的关注对象 id 列表（关注时间正序）；上限由调用方控制 */
    List<Long> findFolloweeIds(Long followerId, int limit);

    /**
     * 粉丝分页（CR-130）：关注了 followee 的账号 id，关注时间倒序。
     * 两条过滤与分页在**同一条 SQL**里：已注销账号剔除（保证"列出来就点得开"）、
     * viewer 已拉黑的账号剔除（CR-129 读侧口径）；{@code countFollowers} 用同一 WHERE，total 同源。
     */
    List<Long> pageFollowerIds(Long followeeId, Long viewerId, int offset, int limit);

    long countFollowers(Long followeeId, Long viewerId);

    /** 关注分页（CR-130）：follower 关注的账号 id，关注时间倒序；过滤口径同 {@link #pageFollowerIds} */
    List<Long> pageFolloweeIds(Long followerId, Long viewerId, int offset, int limit);

    long countFollowing(Long followerId, Long viewerId);
}
