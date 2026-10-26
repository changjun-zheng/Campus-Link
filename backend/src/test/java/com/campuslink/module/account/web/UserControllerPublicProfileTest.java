package com.campuslink.module.account.web;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.common.web.PublicEndpoint;
import com.campuslink.module.account.application.AccountApplicationService;
import com.campuslink.module.account.application.ProfileApplicationService;
import com.campuslink.module.account.application.UserProfileApplicationService;
import com.campuslink.module.account.application.UserProfileApplicationService.PublicProfile;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 他人主页资料端点（{@code GET /api/v1/users/{id}}，F-ACC-002 本体最小版）的契约面。
 *
 * <p>三格各守一头，都不是"跑一遍看看"：
 * ① <b>匿名可读靠接口形状证明</b>——方法签名里没有 {@code Authentication} 形参、且只有 {@code @PublicEndpoint}
 *    没有 {@code @SecurityRequirement}（G7 要求两者恰好其一）。这一格值得写死：主页若哪天被顺手加上登录要求，
 *    "关注了人却没地方看人"这条立论就整条消失，而它在业务测试里看不出来；
 * ② <b>出参字段清单负向断言</b>——公开资料一旦漏出 email / studentId / role / status，就是数据泄露，
 *    而这类泄露在"正常返回"的正向断言里永远绿；
 * ③ <b>2007 原样透传</b>——不存在与已注销同码，控制器不做二次判定（判定在 application 的可见性门里）；
 * ④ <b>IP 接线</b>——限流判据在 application，但 IP 是控制器提的：接错这一根线（比如传成 userId），
 *    限流会变成按目标账号限——爬取者换着 id 爬永远不限，被爬的人反而被打成 429。
 */
class UserControllerPublicProfileTest {

    private static final long USER_ID = 42L;
    private static final Instant CREATED_AT = Instant.parse("2026-09-01T08:00:00Z");

    private final UserProfileApplicationService userProfileApplicationService =
            mock(UserProfileApplicationService.class);
    private final UserController controller = new UserController(mock(AccountApplicationService.class),
            mock(ProfileApplicationService.class), userProfileApplicationService);

    @Test
    @DisplayName("匿名可读：方法不收 Authentication，只挂 @PublicEndpoint（不挂 @SecurityRequirement）")
    void profileIsAnonymousReadableByShape() throws NoSuchMethodException {
        Method profile = UserController.class.getMethod("profile", long.class, HttpServletRequest.class);

        assertThat(Arrays.stream(profile.getParameterTypes())).doesNotContain(Authentication.class);
        assertThat(profile.isAnnotationPresent(PublicEndpoint.class)).isTrue();
        assertThat(profile.isAnnotationPresent(SecurityRequirement.class)).isFalse();
    }

    @Test
    @DisplayName("正常返回：展示列（含入学年级）+ 六个计数 + 等级组逐位落到 VO（long 计数饱和收窄成 int）")
    void mapsProfileAndCountsOntoVo() {
        when(userProfileApplicationService.publicProfileOf(eq(USER_ID), anyString())).thenReturn(new PublicProfile(
                USER_ID, "张三同学", "软件工程", "在写代码", CREATED_AT, "2024",
                12L, 7L, 30L, 3L, 109L, 2, "学徒", 200L, 39, 34L, 5L));

        UserProfileVo vo = controller.profile(USER_ID, new MockHttpServletRequest()).data();

        assertThat(vo.id()).isEqualTo(USER_ID);
        assertThat(vo.nickname()).isEqualTo("张三同学");
        assertThat(vo.major()).isEqualTo("软件工程");
        assertThat(vo.bio()).isEqualTo("在写代码");
        assertThat(vo.createdAt()).isEqualTo(CREATED_AT);
        assertThat(vo.grade()).isEqualTo("2024");
        assertThat(vo.postCount()).isEqualTo(12);
        assertThat(vo.replyCount()).isEqualTo(7);
        assertThat(vo.likeCount()).isEqualTo(30);
        assertThat(vo.acceptedCount()).isEqualTo(3);
        assertThat(vo.reputation()).isEqualTo(109);
        assertThat(vo.level()).isEqualTo(2);
        assertThat(vo.levelName()).isEqualTo("学徒");
        assertThat(vo.nextLevelAt()).isEqualTo(200L);
        assertThat(vo.levelProgress()).isEqualTo(39);
        assertThat(vo.followerCount()).isEqualTo(34);
        assertThat(vo.followingCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("用户不存在 / 已注销 → 2007 原样透传，控制器不兜住也不改写")
    void notFoundPropagates() {
        when(userProfileApplicationService.publicProfileOf(eq(USER_ID), anyString()))
                .thenThrow(new ApiException(ResultCode.USER_NOT_FOUND));

        assertThatThrownBy(() -> controller.profile(USER_ID, new MockHttpServletRequest()))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.USER_NOT_FOUND));
    }

    @Test
    @DisplayName("限流拒绝 → 2010 / 429 原样透传（判据在 application，控制器只声明进契约）")
    void rateLimitPropagates() {
        when(userProfileApplicationService.publicProfileOf(eq(USER_ID), anyString()))
                .thenThrow(new ApiException(ResultCode.PROFILE_VIEW_RATE_LIMITED));

        assertThatThrownBy(() -> controller.profile(USER_ID, new MockHttpServletRequest()))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.PROFILE_VIEW_RATE_LIMITED));
    }

    @Test
    @DisplayName("IP 接线：无 X-Forwarded-For 取 remoteAddr，有则取首段——传给 application 的是 IP 不是 userId")
    void passesClientIpExtractedFromRequest() {
        when(userProfileApplicationService.publicProfileOf(eq(USER_ID), anyString())).thenReturn(new PublicProfile(
                USER_ID, "张三同学", "软件工程", "在写代码", CREATED_AT, null,
                0L, 0L, 0L, 0L, 0L, 1, "初入", 50L, 0, 0L, 0L));

        MockHttpServletRequest direct = new MockHttpServletRequest();
        direct.setRemoteAddr("10.0.0.8");
        controller.profile(USER_ID, direct);
        verify(userProfileApplicationService).publicProfileOf(USER_ID, "10.0.0.8");

        MockHttpServletRequest proxied = new MockHttpServletRequest();
        proxied.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.1");
        controller.profile(USER_ID, proxied);
        verify(userProfileApplicationService).publicProfileOf(USER_ID, "203.0.113.9");
    }

    @Test
    @DisplayName("契约级负向断言：公开资料 VO 不含敏感列、不含本人视角列、也不含需要 viewer 的 following")
    void profileVoCarriesOnlyPublicColumns() {
        List<String> fields = Arrays.stream(UserProfileVo.class.getRecordComponents())
                .map(RecordComponent::getName).toList();

        // grade 是注册时快照的名册入学年级（CR-145），公开展示列；仍不含 school / avatarUrl（无写入也无展示位）
        assertThat(fields).containsExactly("id", "nickname", "major", "bio", "createdAt", "grade",
                "postCount", "replyCount", "likeCount", "acceptedCount",
                "reputation", "level", "levelName", "nextLevelAt", "levelProgress",
                "followerCount", "followingCount");
        assertThat(fields).doesNotContain("email", "emailHash", "emailEnc", "phone", "studentId", "studentIdHash",
                "role", "status", "verified", "anonymized", "deleteAt", "accountType", "following",
                "school", "avatarUrl");
    }
}
