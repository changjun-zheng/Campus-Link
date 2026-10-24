package com.campuslink.module.messaging.web;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.messaging.application.MessagingApplicationService;
import com.campuslink.module.messaging.application.cmd.MessagingResults.ConversationPage;
import com.campuslink.module.messaging.application.cmd.MessagingResults.MarkReadResult;
import com.campuslink.module.messaging.application.cmd.MessagingResults.MessageItem;
import com.campuslink.module.messaging.application.cmd.MessagingResults.MessagePage;
import com.campuslink.module.messaging.application.cmd.MessagingResults.UnreadCount;
import com.campuslink.module.messaging.application.cmd.SendMessageCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 私信 5 个受保护端点（F-SOC-003 / CR-135）的授权三态：匿名一律 4001 且不触达用例；
 * 登录态 userId 只从令牌取，{@code peerId} 是对方 id、不是"我"。
 */
class MessageControllerAuthTest {

    private static final long PEER_ID = 8L;

    private final MessagingApplicationService service = mock(MessagingApplicationService.class);
    private final MessageController controller = new MessageController(service);

    @Test
    @DisplayName("匿名查会话列表 → 4001，且不触达用例")
    void anonymousConversationsIsUnauthorized() {
        assertThatThrownBy(() -> controller.listConversations(null, 1, 20))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));
        verify(service, never()).listConversations(anyLong(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("匿名查未读数 → 4001")
    void anonymousUnreadCountIsUnauthorized() {
        assertThatThrownBy(() -> controller.unreadMessageCount(null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));
        verify(service, never()).unreadCount(anyLong());
    }

    @Test
    @DisplayName("匿名查消息 → 4001，不得读他人会话")
    void anonymousMessagesIsUnauthorized() {
        assertThatThrownBy(() -> controller.listMessages(PEER_ID, null, 1, 20))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));
        verify(service, never()).listMessages(anyLong(), anyLong(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("匿名发送 → 4001，且不触达发送用例")
    void anonymousSendIsUnauthorized() {
        assertThatThrownBy(() -> controller.sendMessage(PEER_ID, new SendMessageCommand("hi"), null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));
        verify(service, never()).send(anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("匿名标记已读 → 4001")
    void anonymousMarkReadIsUnauthorized() {
        assertThatThrownBy(() -> controller.markConversationRead(PEER_ID, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));
        verify(service, never()).markRead(anyLong(), anyLong());
    }

    @Test
    @DisplayName("登录态：userId 只从令牌取（1），peerId 原样透传")
    void loggedInPassesPrincipalId() {
        Authentication auth = new UsernamePasswordAuthenticationToken(1L, "", List.of());
        when(service.listConversations(1L, 1, 20)).thenReturn(new ConversationPage(List.of(), 0L, 1, 20));
        when(service.unreadCount(1L)).thenReturn(new UnreadCount(3L));
        when(service.listMessages(1L, PEER_ID, 1, 20)).thenReturn(new MessagePage(List.of(), 0L, 1, 20));
        when(service.send(1L, PEER_ID, "hi")).thenReturn(
                new MessageItem(1L, 1L, PEER_ID, "hi", Instant.now(), null));
        when(service.markRead(1L, PEER_ID)).thenReturn(new MarkReadResult(1));

        controller.listConversations(auth, 1, 20);
        controller.unreadMessageCount(auth);
        controller.listMessages(PEER_ID, auth, 1, 20);
        controller.sendMessage(PEER_ID, new SendMessageCommand("hi"), auth);
        controller.markConversationRead(PEER_ID, auth);

        verify(service).listConversations(1L, 1, 20);
        verify(service).unreadCount(1L);
        verify(service).listMessages(1L, PEER_ID, 1, 20);
        verify(service).send(1L, PEER_ID, "hi");
        verify(service).markRead(1L, PEER_ID);
    }
}
