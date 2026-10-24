package com.campuslink.module.account.application;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.account.application.cmd.FollowUsers;
import com.campuslink.module.account.domain.gateway.AccountRepository;
import com.campuslink.module.account.domain.gateway.FollowRepository;
import com.campuslink.module.account.domain.model.Account;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 关注用例（F-SOC-002 提前 / CR-074）：关注 / 取关 / 关注状态 / 关注 id 列表。
 * 目标账号存在性经同上下文 {@link AccountApplicationService#accountOf}（不存在 → 2007）；
 * 不能关注自己 → 2009；重复 follow / 未关注即 unfollow 均幂等（仓储实现兜底）。
 * 关注动作**不产生通知**——避免新增通知 type 与前端文案链（CR-066 先例，实施方案 §2 已登记）。
 *
 * <p>CR-130 补粉丝 / 关注两个分页读：目标存在性用 {@link AccountApplicationService#publicAccountOf}
 * （不存在与已注销统一 2007，不给按 id 探测账号状态的口子）；列表条目由仓储 SQL 一次过滤
 * （已注销 + viewer 已拉黑），此处只做分页归一与账号列组装。
 */
@Service
@RequiredArgsConstructor
public class FollowApplicationService {

    private static final int MAX_PAGE_SIZE = 100;

    private final FollowRepository followRepository;
    private final AccountRepository accountRepository;
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

    /**
     * 粉丝列表（CR-130）：target 的粉丝，按关注时间倒序。
     *
     * <p>target 自身可以是 viewer（看自己的粉丝列表）；不存在或已注销 → 2007 / 404。
     */
    public FollowUsers pageFollowers(long viewerId, long targetId, int page, int size) {
        accountApplicationService.publicAccountOf(targetId);
        int currentPage = normalizePage(page);
        int pageSize = normalizeSize(size);
        long total = followRepository.countFollowers(targetId, viewerId);
        List<Long> ids = followRepository.pageFollowerIds(targetId, viewerId, offsetOf(currentPage, pageSize), pageSize);
        return assemble(ids, total, currentPage, pageSize);
    }

    /** 关注列表（CR-130）：target 关注的人，按关注时间倒序；过滤与分页口径同 {@link #pageFollowers} */
    public FollowUsers pageFollowing(long viewerId, long targetId, int page, int size) {
        accountApplicationService.publicAccountOf(targetId);
        int currentPage = normalizePage(page);
        int pageSize = normalizeSize(size);
        long total = followRepository.countFollowing(targetId, viewerId);
        List<Long> ids = followRepository.pageFolloweeIds(targetId, viewerId, offsetOf(currentPage, pageSize), pageSize);
        return assemble(ids, total, currentPage, pageSize);
    }

    private FollowUsers assemble(List<Long> ids, long total, int page, int size) {
        if (ids.isEmpty()) {
            // 空页不查账号表：offset 超出 total 是正常分页结果，不是异常
            return new FollowUsers(List.of(), total, page, size);
        }
        // 条目资料一次批量取（同 UserBlockApplicationService#pageBlocked），列表内不逐条查
        Map<Long, Account> accounts = accountRepository.findByIds(ids).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));
        List<FollowUsers.FollowUser> items = ids.stream()
                .map(accounts::get)
                .filter(Objects::nonNull)
                .map(a -> new FollowUsers.FollowUser(a.getId(), a.getNickname(), a.getMajor(), a.getBio()))
                .toList();
        return new FollowUsers(items, total, page, size);
    }

    private static int normalizePage(int page) {
        return Math.max(page, 1);
    }

    private static int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    /** 偏移量防溢出：page 由客户端传入，{@code (page-1)*size} 在极端值下会溢出 int */
    private static int offsetOf(int page, int size) {
        return (int) Math.min((long) (page - 1) * size, Integer.MAX_VALUE);
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
