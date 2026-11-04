package com.campuslink.module.forum.web;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.forum.application.ContentModerationApplicationService;
import com.campuslink.module.forum.application.cmd.ModerationCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 内容处置 2 个端点（CR-066，F-SAFE-003）的授权三态：匿名 401、普通登录用户 403、SUPERADMIN 放行。
 *
 * <p>⚠️ 与既有 {@code PostControllerAuthTest} 的差别是这里多了一档**角色**：路径级拦截
 * （{@code EndpointAuthorizationManager}）只区分"登录 / 未登录"，"仅 SUPERADMIN"这条**只在
 * {@code CurrentUser.requireRole} 里存在**——所以 ROLE_USER 这一例是本批真正的防线：
 * 摘掉 requireRole 改回 requireId，它就会变红（而匿名例仍绿，容易误以为安全）。
 */
class AdminModerationControllerAuthTest {

    private final ContentModerationApplicationService moderationService =
            mock(ContentModerationApplicationService.class);
    private final AdminModerationController controller = new AdminModerationController(moderationService);

    @Test
    @DisplayName("匿名下架帖子 → 4001，且不触达用例")
    void anonymousPostModerationIsUnauthorized() {
        assertThatThrownBy(() -> controller.moderatePost(9L, command(), null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));
        verify(moderationService, never()).moderatePost(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("普通用户下架帖子 → 4002（角色判定归业务侧，框架不做角色判定），且不触达用例")
    void ordinaryUserPostModerationIsForbidden() {
        assertThatThrownBy(() -> controller.moderatePost(9L, command(), user()))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.FORBIDDEN));
        verify(moderationService, never()).moderatePost(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("OPS 直接处置帖子 / 楼层 → 4002（工单 REMOVE 走工单链路，处置台仍仅 SUPERADMIN）")
    void opsCannotModerateDirectly() {
        assertThatThrownBy(() -> controller.moderatePost(9L, command(), ops()))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.FORBIDDEN));
        assertThatThrownBy(() -> controller.moderateReply(11L, command(), ops()))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.FORBIDDEN));

        verify(moderationService, never()).moderatePost(anyLong(), anyLong(), any());
        verify(moderationService, never()).moderateReply(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("SUPERADMIN 下架帖子 → 200，操作人 id 与目标与命令一起透传")
    void superadminPostModerationPassesThrough() {
        assertThat(controller.moderatePost(9L, command(), superadmin()).code()).isZero();
        verify(moderationService).moderatePost(eq(7L), eq(9L), any(ModerationCommand.class));
    }

    @Test
    @DisplayName("匿名 / 普通用户处置楼层 → 4001 / 4002，都不触达用例")
    void replyModerationRequiresSuperadmin() {
        assertThatThrownBy(() -> controller.moderateReply(11L, command(), null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));
        assertThatThrownBy(() -> controller.moderateReply(11L, command(), user()))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.FORBIDDEN));

        verify(moderationService, never()).moderateReply(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("SUPERADMIN 恢复楼层 → 放行（恢复与下架同一端点、同一权限，靠 body 的 status 区分）")
    void superadminReplyRestorePassesThrough() {
        controller.moderateReply(11L, new ModerationCommand("PUBLISHED", "误判已复核"), superadmin());

        verify(moderationService).moderateReply(7L, 11L, new ModerationCommand("PUBLISHED", "误判已复核"));
    }

    @Test
    @DisplayName("principal 不是账号 id（匿名令牌的字符串主体）→ 4001，不当成已登录")
    void nonUserIdPrincipalIsUnauthorized() {
        Authentication anonymousToken = new UsernamePasswordAuthenticationToken("anonymousUser", null, List.of());

        assertThatThrownBy(() -> controller.moderatePost(9L, command(), anonymousToken))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));
    }

    @Test
    @DisplayName("用例抛 3001 时原样透出（Controller 不自拼错误体，交给 GlobalExceptionHandler）")
    void domainNotFoundPropagatesUnchanged() {
        doThrow(new ApiException(ResultCode.NOT_FOUND)).when(moderationService).moderatePost(7L, 9L, command());

        assertThatThrownBy(() -> controller.moderatePost(9L, command(), superadmin()))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_FOUND));
    }

    private static ModerationCommand command() {
        return new ModerationCommand("REMOVED", "广告内容");
    }

    private static Authentication user() {
        return new UsernamePasswordAuthenticationToken(
                42L, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private static Authentication superadmin() {
        return new UsernamePasswordAuthenticationToken(
                7L, null, List.of(new SimpleGrantedAuthority("ROLE_SUPERADMIN")));
    }

    private static Authentication ops() {
        return new UsernamePasswordAuthenticationToken(
                8L, null, List.of(new SimpleGrantedAuthority("ROLE_OPS")));
    }
}
