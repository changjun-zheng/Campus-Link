package com.campuslink.module.account.application;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.account.application.UserProfileApplicationService.PublicProfile;
import com.campuslink.module.account.domain.gateway.FollowRepository;
import com.campuslink.module.account.domain.gateway.RateLimitGateway;
import com.campuslink.module.account.domain.model.Account;
import com.campuslink.module.account.domain.model.AccountRole;
import com.campuslink.module.account.domain.model.AccountStatus;
import com.campuslink.module.account.domain.model.EmailAddress;
import com.campuslink.module.forum.application.ForumQueryApplicationService;
import com.campuslink.module.forum.application.cmd.ForumResults.AuthorStats;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 他人主页资料卡的读时聚合（{@code publicProfileOf}）与 IP 限流门。
 *
 * <p>聚合这里能测的只有**接线**：六个计数各自取自哪个端口（其中帖子 / 回帖 / 获赞 / 被采纳四个来自 forum 侧
 * <b>同一次</b> {@code authorStats} 调用，CR-104 / CR-138——四个数分四次取就允许它们来自四个时刻，同一张卡上会自相矛盾）。取值的正确性由各自的测试守
 * （{@code PostRepositoryImplCountVisibleByAuthorTest} 守帖子计数的 SQL 条件，
 * {@code FollowRepositoryImpl} 的两个 count 与关注状态端点共用同一对方法）——
 * 本类的价值在于"接错一根线"会立刻变红，尤其是 {@code followerCount} 与 {@code followingCount}
 * 这对只差一个词、错了页面也照样渲染的字段（粉丝数与关注数互换在 UI 上看不出来，只有对着数据才发现）。
 */
@ExtendWith(MockitoExtension.class)
class UserProfileApplicationServiceTest {

    private static final long USER_ID = 42L;
    private static final String IP = "10.0.0.8";
    private static final Instant CREATED_AT = Instant.parse("2026-09-01T08:00:00Z");

    @Mock
    private AccountApplicationService accountApplicationService;
    @Mock
    private FollowRepository followRepository;
    @Mock
    private ForumQueryApplicationService forumQueryService;
    @Mock
    private RateLimitGateway rateLimitGateway;

    @InjectMocks
    private UserProfileApplicationService service;

    @Test
    @DisplayName("资料卡：展示四列取自账号聚合，六个计数各归其位（粉丝=countByFollowee，关注=countByFollower）")
    void aggregatesProfileAndCounts() {
        when(rateLimitGateway.hitAndCount(anyString(), any())).thenReturn(1L);
        when(accountApplicationService.publicAccountOf(USER_ID)).thenReturn(account());
        when(forumQueryService.authorStats(USER_ID)).thenReturn(new AuthorStats(12L, 30L, 3L, 7L));
        when(followRepository.countByFollowee(USER_ID)).thenReturn(34L);
        when(followRepository.countByFollower(USER_ID)).thenReturn(5L);

        PublicProfile profile = service.publicProfileOf(USER_ID, IP);

        assertThat(profile.id()).isEqualTo(USER_ID);
        assertThat(profile.nickname()).isEqualTo("张三同学");
        assertThat(profile.major()).isEqualTo("软件工程");
        assertThat(profile.bio()).isEqualTo("在写代码");
        assertThat(profile.createdAt()).isEqualTo(CREATED_AT);
        assertThat(profile.postCount()).isEqualTo(12L);
        assertThat(profile.replyCount()).isEqualTo(7L);
        assertThat(profile.likeCount()).isEqualTo(30L);
        assertThat(profile.acceptedCount()).isEqualTo(3L);
        assertThat(profile.followerCount()).isEqualTo(34L);
        assertThat(profile.followingCount()).isEqualTo(5L);
    }

    @Test
    @DisplayName("声望按四项加权读时算出，档位与进度同步给出：3 采纳 + 30 赞 + 12 帖 + 7 回帖 = 109 → Lv2 / 39%")
    void reputationAndLevelAreDerivedFromTheSameCounts() {
        when(rateLimitGateway.hitAndCount(anyString(), any())).thenReturn(1L);
        when(accountApplicationService.publicAccountOf(USER_ID)).thenReturn(account());
        when(forumQueryService.authorStats(USER_ID)).thenReturn(new AuthorStats(12L, 30L, 3L, 7L));
        when(followRepository.countByFollowee(USER_ID)).thenReturn(0L);
        when(followRepository.countByFollower(USER_ID)).thenReturn(0L);

        PublicProfile profile = service.publicProfileOf(USER_ID, IP);

        assertThat(profile.reputation()).isEqualTo(109L);
        assertThat(profile.level()).isEqualTo(2);
        assertThat(profile.levelName()).isEqualTo("学徒");
        assertThat(profile.nextLevelAt()).isEqualTo(200L);
        assertThat(profile.levelProgress()).isEqualTo(39);
    }

    @Test
    @DisplayName("满级：nextLevelAt 为 null（「没有下一级」不是 0），进度恒 100")
    void topLevelHasNoNextLevelAt() {
        when(rateLimitGateway.hitAndCount(anyString(), any())).thenReturn(1L);
        when(accountApplicationService.publicAccountOf(USER_ID)).thenReturn(account());
        // 250 次被采纳 = 2500 声望，已越过社区之星门槛 2000
        when(forumQueryService.authorStats(USER_ID)).thenReturn(new AuthorStats(0L, 0L, 250L, 0L));
        when(followRepository.countByFollowee(USER_ID)).thenReturn(0L);
        when(followRepository.countByFollower(USER_ID)).thenReturn(0L);

        PublicProfile profile = service.publicProfileOf(USER_ID, IP);

        assertThat(profile.reputation()).isEqualTo(2500L);
        assertThat(profile.level()).isEqualTo(6);
        assertThat(profile.levelName()).isEqualTo("社区之星");
        assertThat(profile.nextLevelAt()).isNull();
        assertThat(profile.levelProgress()).isEqualTo(100);
    }

    @Test
    @DisplayName("计数为零也要如实回 0，不回落成 null（前端「帖子 0」与「加载中」是两种状态）")
    void zeroCountsAreReportedAsZero() {
        when(rateLimitGateway.hitAndCount(anyString(), any())).thenReturn(1L);
        when(accountApplicationService.publicAccountOf(USER_ID)).thenReturn(account());
        when(forumQueryService.authorStats(USER_ID)).thenReturn(new AuthorStats(0L, 0L, 0L, 0L));
        when(followRepository.countByFollowee(USER_ID)).thenReturn(0L);
        when(followRepository.countByFollower(USER_ID)).thenReturn(0L);

        PublicProfile profile = service.publicProfileOf(USER_ID, IP);

        assertThat(profile.postCount()).isZero();
        assertThat(profile.replyCount()).isZero();
        assertThat(profile.likeCount()).isZero();
        assertThat(profile.acceptedCount()).isZero();
        assertThat(profile.followerCount()).isZero();
        assertThat(profile.followingCount()).isZero();
        assertThat(profile.reputation()).isZero();
        assertThat(profile.level()).isEqualTo(1);
        assertThat(profile.levelName()).isEqualTo("初入");
        assertThat(profile.nextLevelAt()).isEqualTo(50L);
        assertThat(profile.levelProgress()).isZero();
    }

    @Test
    @DisplayName("账号不可公开（不存在 / 已注销）→ 2007，且六个计数一个都不查（不给探针当放大器）")
    void invisibleAccountShortCircuitsBeforeCounting() {
        when(rateLimitGateway.hitAndCount(anyString(), any())).thenReturn(1L);
        when(accountApplicationService.publicAccountOf(USER_ID))
                .thenThrow(new ApiException(ResultCode.USER_NOT_FOUND));

        assertThatThrownBy(() -> service.publicProfileOf(USER_ID, IP))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.USER_NOT_FOUND));

        verify(forumQueryService, never()).authorStats(USER_ID);
        verify(followRepository, never()).countByFollowee(USER_ID);
        verify(followRepository, never()).countByFollower(USER_ID);
    }

    @Test
    @DisplayName("限流键是 IP 维度不是账号维度：profile:view:<ip>，窗口 1 分钟（公开端点，匿名也要被限）")
    void rateLimitKeyIsPerIpWithOneMinuteWindow() {
        when(rateLimitGateway.hitAndCount(anyString(), any())).thenReturn(1L);
        when(accountApplicationService.publicAccountOf(USER_ID)).thenReturn(account());
        when(forumQueryService.authorStats(USER_ID)).thenReturn(new AuthorStats(0L, 0L, 0L, 0L));
        when(followRepository.countByFollowee(USER_ID)).thenReturn(0L);
        when(followRepository.countByFollower(USER_ID)).thenReturn(0L);

        service.publicProfileOf(USER_ID, IP);

        verify(rateLimitGateway).hitAndCount("profile:view:" + IP, Duration.ofMinutes(1));
    }

    @Test
    @DisplayName("限流判据是先增后判：第 30 次放行、第 31 次拒 2010（写成 >= 会把配额悄悄砍成 29 次）")
    void thirtiethHitPassesAndThirtyFirstRejects() {
        when(rateLimitGateway.hitAndCount(anyString(), any())).thenReturn(30L);
        when(accountApplicationService.publicAccountOf(USER_ID)).thenReturn(account());
        when(forumQueryService.authorStats(USER_ID)).thenReturn(new AuthorStats(0L, 0L, 0L, 0L));
        when(followRepository.countByFollowee(USER_ID)).thenReturn(0L);
        when(followRepository.countByFollower(USER_ID)).thenReturn(0L);

        service.publicProfileOf(USER_ID, IP);

        when(rateLimitGateway.hitAndCount(anyString(), any())).thenReturn(31L);
        assertThatThrownBy(() -> service.publicProfileOf(USER_ID, IP))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.PROFILE_VIEW_RATE_LIMITED));
    }

    @Test
    @DisplayName("超限那一趟**不触达任何读库**：限流门在可见性门之前（不给探针当放大器，与 2007 短路同一纪律）")
    void overLimitShortCircuitsBeforeAnyRead() {
        when(rateLimitGateway.hitAndCount(anyString(), any())).thenReturn(99L);

        assertThatThrownBy(() -> service.publicProfileOf(USER_ID, IP))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.PROFILE_VIEW_RATE_LIMITED));

        verify(accountApplicationService, never()).publicAccountOf(USER_ID);
        verify(forumQueryService, never()).authorStats(USER_ID);
        verify(followRepository, never()).countByFollowee(USER_ID);
        verify(followRepository, never()).countByFollower(USER_ID);
    }

    private static Account account() {
        return Account.rehydrate(USER_ID, EmailAddress.of("dev-stu-01@dev.campuslink.local"), null, null, "张三同学", null, null, "软件工程", null, "在写代码", AccountRole.USER, AccountStatus.ACTIVE, true, false, null, CREATED_AT, null);
    }
}
