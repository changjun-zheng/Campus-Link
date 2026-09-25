package com.campuslink.module.forum.web;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.forum.application.ForumQueryApplicationService;
import com.campuslink.module.forum.application.PostApplicationService;
import com.campuslink.module.forum.application.cmd.ForumResults.PublishedPost;
import com.campuslink.module.forum.application.cmd.ForumResults.SimilarPostResult;
import com.campuslink.module.forum.application.cmd.PublishPostCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 发帖端点的授权三态：匿名 / principal 非用户 ID（如匿名令牌的字符串主体）必须 401 且不触达用例，
 * 已登录则放行并把操作人 ID 从 principal 透传给用例。
 *
 * <p>为什么框架已按注解拦截（CR-031，{@code security/EndpointAuthorizationManager}）之后仍需这组断言：
 * 路径级拦截只判"有没有登录"，判不了"principal 是不是账号 id"这类主体形状问题，也无法证明**用例未被触达**；
 * 且它守的是一段历史教训——{@code SecurityConfig} 曾长期 {@code anyRequest().permitAll()}，
 * 那时漏写 Controller 校验**不会让任何测试失败**，只会静默把发帖变成公开接口，
 * 而契约快照上的 {@code security} 声明反而制造"已受保护"的错觉
 * （W-07 推迟 A3-9 的直接后果，见 docs/流程偏离记录.md；设计 §4.2）。
 */
class PostControllerAuthTest {

    private static final PublishPostCommand COMMAND = new PublishPostCommand("qna", "标题", "正文", null);

    private final ForumQueryApplicationService forumQueryService = mock(ForumQueryApplicationService.class);
    private final PostApplicationService postApplicationService = mock(PostApplicationService.class);
    private final com.campuslink.module.forum.application.InteractionApplicationService interactionApplicationService =
            mock(com.campuslink.module.forum.application.InteractionApplicationService.class);
    private final PostController controller = new PostController(forumQueryService, postApplicationService,
            interactionApplicationService, null);

    @Test
    @DisplayName("匿名发帖 → 4001，且不触达发帖用例")
    void anonymousIsUnauthorized() {
        assertThatThrownBy(() -> controller.publish(COMMAND, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));

        verify(postApplicationService, never()).publish(any(), any());
    }

    @Test
    @DisplayName("principal 不是用户 ID → 4001（匿名令牌的字符串主体不得被当成已登录）")
    void nonUserIdPrincipalIsUnauthorized() {
        Authentication anonymousToken = new UsernamePasswordAuthenticationToken("anonymousUser", null, List.of());

        assertThatThrownBy(() -> controller.publish(COMMAND, anonymousToken))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));

        verify(postApplicationService, never()).publish(any(), any());
    }

    @Test
    @DisplayName("已登录 → 放行，操作人 ID 取自 principal")
    void authorIdComesFromPrincipal() {
        when(postApplicationService.publish(eq(42L), any())).thenReturn(new PublishedPost(123L));

        var response = controller.publish(COMMAND, user());

        assertThat(response.data().id()).isEqualTo(123L);
        verify(postApplicationService).publish(eq(42L), eq(COMMAND));
    }

    @Test
    @DisplayName("匿名采纳 → 4001，且不触达采纳用例")
    void anonymousAcceptIsUnauthorized() {
        var command = new com.campuslink.module.forum.application.cmd.AcceptReplyCommand(456L);

        assertThatThrownBy(() -> controller.accept(123L, command, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));

        verify(postApplicationService, never()).acceptReply(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("已登录采纳 → 放行，操作人与目标从参数透传")
    void acceptPassesPrincipalAndIds() {
        var command = new com.campuslink.module.forum.application.cmd.AcceptReplyCommand(456L);

        var response = controller.accept(123L, command, user());

        assertThat(response.code()).isEqualTo(0);
        verify(postApplicationService).acceptReply(eq(42L), eq(123L), eq(456L));
    }

    @Test
    @DisplayName("匿名删除 → 4001，且不触达删除用例")
    void anonymousDeleteIsUnauthorized() {
        assertThatThrownBy(() -> controller.delete(123L, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));

        verify(postApplicationService, never()).deletePost(anyLong(), anyLong());
    }

    @Test
    @DisplayName("已登录删除 → 放行，操作人与目标帖子从参数透传")
    void deletePassesPrincipalAndPostId() {
        var response = controller.delete(123L, user());

        assertThat(response.code()).isEqualTo(0);
        verify(postApplicationService).deletePost(eq(42L), eq(123L));
    }

    @Test
    @DisplayName("匿名取「我的帖子」→ 4001，且不触达查询用例（REMOVED 内容不能对匿名露出）")
    void anonymousMyPostsIsUnauthorized() {
        assertThatThrownBy(() -> controller.myPosts(null, 1, 20))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));

        verify(forumQueryService, never()).listMyPosts(anyLong(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("已登录取「我的帖子」→ 作者 id 只从 principal 来（签名里没有 authorId 形参）")
    void myPostsTakesAuthorFromPrincipal() {
        when(forumQueryService.listMyPosts(eq(42L), eq(1), eq(20)))
                .thenReturn(new com.campuslink.module.forum.domain.gateway.PageResult<>(List.of(), 0, 1, 20));

        var response = controller.myPosts(user(), 1, 20);

        assertThat(response.data().list()).isEmpty();
        verify(forumQueryService).listMyPosts(42L, 1, 20);
    }

    @Test
    @DisplayName("匿名访问相似帖子推荐 → 4001，且不触达查询用例")
    void anonymousSimilarIsUnauthorized() {
        assertThatThrownBy(() -> controller.similar("如何学习Java编程", null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));

        verify(forumQueryService, never()).findSimilarPosts(any(), any());
    }

    @Test
    @DisplayName("已登录访问相似帖子推荐 → 放行，用户 ID 从 principal 取")
    void similarPassesPrincipalToService() {
        Instant now = Instant.parse("2026-09-12T08:00:00Z");
        when(forumQueryService.findSimilarPosts(eq("如何学习Java编程"), eq(42L)))
                .thenReturn(List.of(new SimilarPostResult(10L, "Java入门", "qna", "技术问答",
                        2, false, now)));

        var response = controller.similar("如何学习Java编程", user());

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).hasSize(1);
        assertThat(response.data().get(0).id()).isEqualTo(10L);
        assertThat(response.data().get(0).title()).isEqualTo("Java入门");
        verify(forumQueryService).findSimilarPosts("如何学习Java编程", 42L);
    }

    private static Authentication user() {
        return new UsernamePasswordAuthenticationToken(
                42L, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}

