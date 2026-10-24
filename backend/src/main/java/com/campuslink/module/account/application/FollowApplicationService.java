package com.campuslink.module.account.application;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.account.domain.gateway.FollowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 关注用例（F-SOC-002 提前 / CR-074）：关注 / 取关 / 关注状态 / 关注 id 列表。
 * 目标账号存在性经同上下文 {@link AccountApplicationService#accountOf}（不存在 → 2007）；
 * 不能关注自己 → 2009；重复 follow / 未关注即 unfollow 均幂等（仓储实现兜底）。
 * 关注动作**不产生通知**——避免新增通知 type 与前端文案链（CR-066 先例，实施方案 §2 已登记）。
 */
@Service
@RequiredArgsConstructor
public class FollowApplicationService {

    private final FollowRepository followRepository;
    private final AccountApplicationService accountApplicationService;
    private final UserBlockApplicationService userBlockApplicationService;

    public void follow(long followerId, long targetId) {
        requireNotSelf(followerId, targetId);
        accountApplicationService.accountOf(targetId);
        // 拉黑阻断（CR-129）：目标拉黑了关注者 → 统一 403，不提示"你被拉黑"
        if (userBlockApplicationService.isBlocked(targetId, followerId)) {
            throw new ApiException(ResultCode.FORBIDDEN);
        }
        followRepository.follow(followerId, targetId);
    }

    public void unfollow(long followerId, long targetId) {
        accountApplicationService.accountOf(targetId);
        followRepository.unfollow(followerId, targetId);
    }

    public FollowState state(long viewerId, long targetId) {
        accountApplicationService.accountOf(targetId);
        return new FollowState(followRepository.existsByFollowerAndFollowee(viewerId, targetId),
                followRepository.countByFollowee(targetId), followRepository.countByFollower(targetId));
    }

    /** 关注对象 id 列表（供 forum 关注 Feed 消费；上限在方案 R2 定为 500） */
    public List<Long> followingIds(long userId) {
        return followRepository.findFolloweeIds(userId, 500);
    }

    private static void requireNotSelf(long followerId, long targetId) {
        if (followerId == targetId) {
            throw new ApiException(ResultCode.CANNOT_FOLLOW_SELF);
        }
    }

    /** 关注状态出参（following 为 viewer 视角；两个计数是 target 的粉丝数 / 关注数） */
    public record FollowState(boolean following, long followerCount, long followeeCount) {
    }
}
