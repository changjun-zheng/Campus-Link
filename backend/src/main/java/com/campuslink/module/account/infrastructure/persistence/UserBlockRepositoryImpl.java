package com.campuslink.module.account.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campuslink.module.account.domain.gateway.UserBlockRepository;
import com.campuslink.module.account.infrastructure.persistence.mapper.UserBlockMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 适配器：UserBlockRepository 的 MyBatis-Plus 实现（CR-129） */
@Repository
@RequiredArgsConstructor
public class UserBlockRepositoryImpl implements UserBlockRepository {

    private final UserBlockMapper userBlockMapper;

    @Override
    public boolean block(long blockerId, long blockedId) {
        UserBlockDO row = new UserBlockDO();
        row.setBlockerId(blockerId);
        row.setBlockedId(blockedId);
        try {
            return userBlockMapper.insert(row) > 0;
        } catch (DuplicateKeyException e) {
            // 联合主键即幂等键：重复拉黑不报错、不记审计
            return false;
        }
    }

    @Override
    public void unblock(long blockerId, long blockedId) {
        userBlockMapper.delete(new LambdaQueryWrapper<UserBlockDO>()
                .eq(UserBlockDO::getBlockerId, blockerId)
                .eq(UserBlockDO::getBlockedId, blockedId));
    }

    @Override
    public boolean exists(long blockerId, long blockedId) {
        return userBlockMapper.selectCount(new LambdaQueryWrapper<UserBlockDO>()
                .eq(UserBlockDO::getBlockerId, blockerId)
                .eq(UserBlockDO::getBlockedId, blockedId)) > 0;
    }

    @Override
    public Set<Long> blockedIdsOf(long blockerId) {
        return userBlockMapper.selectList(new LambdaQueryWrapper<UserBlockDO>()
                        .eq(UserBlockDO::getBlockerId, blockerId)).stream()
                .map(UserBlockDO::getBlockedId)
                .collect(Collectors.toSet());
    }

    @Override
    public List<Long> pageBlockedIds(long blockerId, int offset, int limit) {
        return userBlockMapper.pageBlockedIds(blockerId, offset, limit);
    }

    @Override
    public long countBlocked(long blockerId) {
        return userBlockMapper.selectCount(new LambdaQueryWrapper<UserBlockDO>()
                .eq(UserBlockDO::getBlockerId, blockerId));
    }
}
