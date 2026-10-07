package com.campuslink.module.forum.web;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.forum.application.ForumQueryApplicationService;
import com.campuslink.module.forum.application.ReplyApplicationService;
import com.campuslink.module.forum.application.cmd.ForumResults.PublishedReply;
import com.campuslink.module.forum.application.cmd.PublishReplyCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 回帖端点的授权：匿名必须 401 且不触达用例。理由与断言必要性见 {@link PostControllerAuthTest}
 * （框架层已按注解拦截匿名，本组断言守的是业务侧边界——见该类的说明）。
 */
class ReplyControllerAuthTest {

    private static final PublishReplyCommand COMMAND = new PublishReplyCommand("回复内容", null);

    private final ForumQueryApplicationService forumQueryService = mock(ForumQueryApplicationService.class);
    private final ReplyApplicationService replyApplicationService = mock(ReplyApplicationService.class);
    private final com.campuslink.module.forum.application.InteractionApplicationService interactionApplicationService =
            mock(com.campuslink.module.forum.application.InteractionApplicationService.class);
    private final ReplyController controller = new ReplyController(forumQueryService, replyApplicationService, interactionApplicationService);

    @Test
    @DisplayName("匿名回帖 → 4001，且不触达回帖用例")
    void anonymousIsUnauthorized() {
        assertThatThrownBy(() -> controller.reply(9L, COMMAND, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));

        verify(replyApplicationService, never()).reply(any(), any(), any());
    }

    @Test
    @DisplayName("principal 不是用户 ID → 4001")
    void nonUserIdPrincipalIsUnauthorized() {
        Authentication anonymousToken = new UsernamePasswordAuthenticationToken("anonymousUser", null, List.of());

        assertThatThrownBy(() -> controller.reply(9L, COMMAND, anonymousToken))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));

        verify(replyApplicationService, never()).reply(any(), any(), any());
    }

    @Test
    @DisplayName("已登录 → 放行，返回本次楼层号")
    void floorNoIsReturned() {
        when(replyApplicationService.reply(eq(9L), eq(42L), any())).thenReturn(new PublishedReply(456L, 7));

        var response = controller.reply(9L, COMMAND, user());

        assertThat(response.data()).isEqualTo(new PublishedReply(456L, 7));
        verify(replyApplicationService).reply(eq(9L), eq(42L), eq(COMMAND));
    }

    private static Authentication user() {
        return new UsernamePasswordAuthenticationToken(
                42L, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
