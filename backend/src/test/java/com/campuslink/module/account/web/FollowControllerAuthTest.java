package com.campuslink.module.account.web;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ApiResponse;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.account.application.FollowApplicationService;
import com.campuslink.module.account.application.cmd.FollowUsers;
import com.campuslink.module.account.web.vo.FollowUsersVo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 粉丝 / 关注列表两个受保护端点（CR-130）的授权三态：匿名一律 4001 且不触达用例；
 * 登录态 viewerId 只从令牌取，page / size 原样透传。
 */
class FollowControllerAuthTest {

    private static final long TARGET_ID = 8L;

    private final FollowApplicationService service = mock(FollowApplicationService.class);
    private final FollowController controller = new FollowController(service);

    @Test
    @DisplayName("匿名查粉丝列表 → 4001，且不触达用例")
    void anonymousFollowersIsUnauthorized() {
        assertThatThrownBy(() -> controller.followers(TARGET_ID, 1, 20, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));
        verify(service, never()).pageFollowers(anyLong(), anyLong(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("匿名查关注列表 → 4001，且不触达用例")
    void anonymousFollowingIsUnauthorized() {
        assertThatThrownBy(() -> controller.followingUsers(TARGET_ID, 1, 20, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));
        verify(service, never()).pageFollowing(anyLong(), anyLong(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("登录查粉丝列表：viewerId 只从令牌取，page / size 透传并映射为 VO")
    void followersPassesPrincipalAndMapsVo() {
        when(service.pageFollowers(1L, TARGET_ID, 2, 10)).thenReturn(new FollowUsers(
                List.of(new FollowUsers.FollowUser(8L, "学生八", "计算机科学与技术", "签名")),
                1L, 2, 10));

        ApiResponse<FollowUsersVo> response = controller.followers(TARGET_ID, 2, 10, auth(1L));

        assertThat(response.data().total()).isEqualTo(1);
        assertThat(response.data().page()).isEqualTo(2);
        assertThat(response.data().size()).isEqualTo(10);
        assertThat(response.data().items())
                .extracting(FollowUsersVo.FollowUserVo::id)
                .containsExactly(8L);
        assertThat(response.data().items().get(0).nickname()).isEqualTo("学生八");
    }

    @Test
    @DisplayName("登录查关注列表：走 pageFollowing（不混用粉丝侧用例）")
    void followingPassesPrincipalAndUsesFolloweeCase() {
        when(service.pageFollowing(1L, TARGET_ID, 1, 20)).thenReturn(new FollowUsers(List.of(), 0L, 1, 20));

        ApiResponse<FollowUsersVo> response = controller.followingUsers(TARGET_ID, 1, 20, auth(1L));

        assertThat(response.data().items()).isEmpty();
        verify(service).pageFollowing(1L, TARGET_ID, 1, 20);
        verify(service, never()).pageFollowers(anyLong(), anyLong(), anyInt(), anyInt());
    }

    private static Authentication auth(long userId) {
        return new UsernamePasswordAuthenticationToken(userId, "", List.of());
    }
}
