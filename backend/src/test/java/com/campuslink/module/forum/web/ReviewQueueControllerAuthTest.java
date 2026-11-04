package com.campuslink.module.forum.web;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ApiResponse;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.forum.application.ReviewQueueApplicationService;
import com.campuslink.module.forum.web.vo.ReviewQueueVo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 巡查队列端点（CR-133 / F-SAFE-004、CR-196）的授权：匿名 401、普通用户 403、OPS / SUPERADMIN 放行。
 *
 * <p>ROLE_USER 那一例是本端点真正的防线：路径级框架只区分「登录 / 未登录」，
 * 「OPS / SUPERADMIN」只存在于 {@code CurrentUser.requireRole}。SUPERADMIN 在真实过滤器里
 * 会同时持有 ROLE_OPS（CR-196 角色层级），本测试显式构造两个 authority 以模拟该事实。
 */
class ReviewQueueControllerAuthTest {

    private final ReviewQueueApplicationService service = mock(ReviewQueueApplicationService.class);
    private final ReviewQueueController controller = new ReviewQueueController(service);

    @Test
    @DisplayName("匿名查巡查队列 → 4001，且不触达用例")
    void anonymousIsUnauthorized() {
        assertThatThrownBy(() -> controller.reviewQueue("post", 1, 20, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));
        verify(service, never()).page(anyString(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("普通用户查巡查队列 → 4002，且不触达用例")
    void ordinaryUserIsForbidden() {
        assertThatThrownBy(() -> controller.reviewQueue("post", 1, 20, auth("ROLE_USER")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.FORBIDDEN));
        verify(service, never()).page(anyString(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("OPS 查巡查队列 → 200（内容审核职责）")
    void opsIsAllowed() {
        when(service.page("post", 1, 20)).thenReturn(new ReviewQueueApplicationService.ReviewQueue(
                "post", List.of(), 0L, 1, 20));

        ApiResponse<ReviewQueueVo> response = controller.reviewQueue("post", 1, 20, auth("ROLE_OPS"));

        assertThat(response.code()).isZero();
        assertThat(response.data().total()).isZero();
    }

    @Test
    @DisplayName("SUPERADMIN → 200，kind 与条目字段按用例结果映射")
    void superadminMapsVo() {
        when(service.page("reply", 2, 10)).thenReturn(new ReviewQueueApplicationService.ReviewQueue(
                "reply",
                List.of(new ReviewQueueApplicationService.ReviewItemView(31, "reply", "楼层摘要", "技术问答",
                        "张三", "REMOVED", Instant.parse("2026-10-24T10:00:00Z"))),
                1L, 2, 10));

        ApiResponse<ReviewQueueVo> response = controller.reviewQueue("reply", 2, 10, superadmin());

        assertThat(response.code()).isZero();
        ReviewQueueVo vo = response.data();
        assertThat(vo.kind()).isEqualTo("reply");
        assertThat(vo.total()).isEqualTo(1);
        assertThat(vo.page()).isEqualTo(2);
        assertThat(vo.items().get(0).id()).isEqualTo(31);
        assertThat(vo.items().get(0).title()).isEqualTo("楼层摘要");
        assertThat(vo.items().get(0).status()).isEqualTo("REMOVED");
    }

    private static Authentication auth(String authority) {
        return new UsernamePasswordAuthenticationToken(1L, "",
                List.of(new SimpleGrantedAuthority(authority)));
    }

    /** 真实过滤器对 SUPERADMIN 同时授予 ROLE_SUPERADMIN + ROLE_OPS（CR-196） */
    private static Authentication superadmin() {
        return new UsernamePasswordAuthenticationToken(1L, "",
                List.of(new SimpleGrantedAuthority("ROLE_SUPERADMIN"),
                        new SimpleGrantedAuthority("ROLE_OPS")));
    }
}
