package com.campuslink.module.account.application;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.account.domain.gateway.FollowRepository;
import com.campuslink.module.account.domain.gateway.RateLimitGateway;
import com.campuslink.module.account.domain.model.Account;
import com.campuslink.module.forum.application.ForumQueryApplicationService;
import com.campuslink.module.forum.application.cmd.ForumResults.AuthorStats;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * 他人主页（{@code GET /api/v1/users/{id}}）的公开资料聚合用例。
 *
 * <p>为什么要单开一个服务而不塞进 {@link AccountApplicationService}：后者是"账号生命周期"
 * （核验 / 注册 / 登录）已依赖 10 个端口；本用例只做一件读时聚合——账号基本列 + 三个来自两个上下文的计数，
 * 与 {@link ProfileApplicationService}（写侧资料编辑）同样是"分开后两条链路各自的构造都不再膨胀"。
 *
 * <p>本类是 account 上下文里**唯一**反向调 forum 的地方（forum 侧为了作者昵称与关注 Feed 已在调 account）。
 * 这不构成 Bean 环：forum 依赖的是 {@link AccountApplicationService} 与 {@link FollowApplicationService}，
 * 两者都不依赖本类。跨上下文只碰对方 application 包（ADR-012 / 守护测试 G4）。
 *
 * <p>三个计数的来源与口径：
 * <ul>
 *   <li>{@code postCount} —— forum 侧 {@code countVisibleByAuthor}，与主页下方那份帖子列表的 WHERE 同源；</li>
 *   <li>{@code followerCount} / {@code followingCount} —— 本上下文 {@code follows} 表的两个计数，
 *       与 {@link FollowApplicationService#state} 用的是同一对仓储方法，故两处数字不会对不上。</li>
 * </ul>
 * 与 {@code FollowApplicationService#state} 的差别在于本用例**没有 viewer**：它是公开端点，匿名也能读，
 * 因此不产出 {@code following}（"我有没有关注他"），前端登录时另调 {@code follow-state} 拿那一位。
 */
@Service
@RequiredArgsConstructor
public class UserProfileApplicationService {

    /** 每分钟每 IP 可查看的主页数：正常用户 1 分钟不会看超过 30 个不同主页，超过即视为爬取 */
    private static final int VIEW_PER_MINUTE_LIMIT = 30;
    private static final Duration VIEW_WINDOW = Duration.ofMinutes(1);

    private final AccountApplicationService accountApplicationService;
    private final FollowRepository followRepository;
    private final ForumQueryApplicationService forumQueryService;
    private final RateLimitGateway rateLimitGateway;

    /**
     * 公开资料：IP 限流 → 账号不存在或已注销 → 2007 / 404。
     *
     * <p>限流放在最前面：超限那一趟不触达任何读库操作（与 {@link ProfileApplicationService} 同范式）。
     * key 格式 {@code profile:view:<ip>}，窗口 1 分钟，阈值 30 次——
     * 判据 {@code hits > limit}（先增后判，与资料编辑限流同口径）。
     *
     * @param userId   目标用户 ID
     * @param clientIp 调用方 IP（由 web 层通过 {@code IpUtil.clientIp(request)} 提取并传入）
     */
    public PublicProfile publicProfileOf(long userId, String clientIp) {
        long hits = rateLimitGateway.hitAndCount("profile:view:" + clientIp, VIEW_WINDOW);
        if (hits > VIEW_PER_MINUTE_LIMIT) {
            throw new ApiException(ResultCode.PROFILE_VIEW_RATE_LIMITED);
        }
        Account account = accountApplicationService.publicAccountOf(userId);
        AuthorStats stats = forumQueryService.authorStats(userId);
        return new PublicProfile(account.getId(), account.getNickname(), account.getMajor(), account.getBio(),
                account.getCreatedAt(),
                stats.postCount(), stats.likeCount(), stats.acceptedCount(),
                followRepository.countByFollowee(userId),
                followRepository.countByFollower(userId));
    }

    /**
     * 公开资料出参（application 层载体，web 层再映射成 {@code UserProfileVo}）。
     *
     * <p>计数用 {@code long}：四个来源（forum 的三个聚合与 {@code follows} 的两个计数）返回的都是 long，
     * 在应用层就窄化等于把"饱和还是抛异常"这个展示层决定提前塞进用例出参；收窄发生在 VO 那一步。
     *
     * <p>{@code likeCount} / {@code acceptedCount}（CR-104）来自 forum 侧**同一次** {@code authorStats} 调用，
     * 与 {@code postCount} 因此来自同一时刻——资料卡上三个数互相矛盾这条路被堵在取数处。
     */
    public record PublicProfile(Long id, String nickname, String major, String bio, Instant createdAt,
                                long postCount, long likeCount, long acceptedCount,
                                long followerCount, long followingCount) {
    }
}
