package com.campuslink.module.account.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campuslink.module.account.domain.gateway.FollowRepository;
import com.campuslink.module.account.infrastructure.persistence.mapper.FollowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;

/** 适配器：FollowRepository 端口的 MyBatis-Plus 实现（唯一键冲突即幂等忽略） */
@Repository
@RequiredArgsConstructor
public class FollowRepositoryImpl implements FollowRepository {

    private final FollowMapper followMapper;

    @Override
    public void follow(Long followerId, Long followeeId) {
        FollowDO d = new FollowDO();
        d.setFollowerId(followerId);
        d.setFolloweeId(followeeId);
        try {
            followMapper.insert(d);
        } catch (DuplicateKeyException ignored) {
            // 重复关注幂等：联合主键 (follower_id, followee_id) 已存在即什么都不做
        }
    }

    @Override
    public void unfollow(Long followerId, Long followeeId) {
        followMapper.delete(new LambdaQueryWrapper<FollowDO>()
                .eq(FollowDO::getFollowerId, followerId)
                .eq(FollowDO::getFolloweeId, followeeId));
    }

    @Override
    public boolean existsByFollowerAndFollowee(Long followerId, Long followeeId) {
        return followMapper.selectCount(new LambdaQueryWrapper<FollowDO>()
                .eq(FollowDO::getFollowerId, followerId)
                .eq(FollowDO::getFolloweeId, followeeId)) > 0;
    }

    @Override
    public long countByFollowee(Long followeeId) {
        return followMapper.selectCount(new LambdaQueryWrapper<FollowDO>()
                .eq(FollowDO::getFolloweeId, followeeId));
    }

    @Override
    public long countByFollower(Long followerId) {
        return followMapper.selectCount(new LambdaQueryWrapper<FollowDO>()
                .eq(FollowDO::getFollowerId, followerId));
    }

    @Override
    public List<Long> findFolloweeIds(Long followerId, int limit) {
        return followMapper.selectList(new LambdaQueryWrapper<FollowDO>()
                        .eq(FollowDO::getFollowerId, followerId)
                        .orderByAsc(FollowDO::getCreatedAt)
                        .last("LIMIT " + Math.max(limit, 1)))
                .stream()
                .map(FollowDO::getFolloweeId)
                .toList();
    }

    @Override
    public List<Long> pageFollowerIds(Long followeeId, Long viewerId, int offset, int limit) {
        return followMapper.pageFollowerIds(followeeId, viewerId, Math.max(offset, 0), Math.max(limit, 1));
    }

    @Override
    public long countFollowers(Long followeeId, Long viewerId) {
        return followMapper.countFollowers(followeeId, viewerId);
    }

    @Override
    public List<Long> pageFolloweeIds(Long followerId, Long viewerId, int offset, int limit) {
        return followMapper.pageFolloweeIds(followerId, viewerId, Math.max(offset, 0), Math.max(limit, 1));
    }

    @Override
    public long countFollowing(Long followerId, Long viewerId) {
        return followMapper.countFollowing(followerId, viewerId);
    }
}
