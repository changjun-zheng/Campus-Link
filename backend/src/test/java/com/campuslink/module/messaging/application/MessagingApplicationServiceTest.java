package com.campuslink.module.messaging.application;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.account.application.AccountApplicationService;
import com.campuslink.module.account.application.UserBlockApplicationService;
import com.campuslink.module.account.domain.model.Account;
import com.campuslink.module.forum.application.ModerationApplicationService;
import com.campuslink.module.messaging.application.cmd.MessagingResults.ConversationPage;
import com.campuslink.module.messaging.application.cmd.MessagingResults.MarkReadResult;
import com.campuslink.module.messaging.application.cmd.MessagingResults.MessageItem;
import com.campuslink.module.messaging.application.cmd.MessagingResults.MessagePage;
import com.campuslink.module.messaging.application.cmd.MessagingResults.UnreadCount;
import com.campuslink.module.messaging.domain.gateway.ConversationRepository;
import com.campuslink.module.messaging.domain.gateway.MessageRepository;
import com.campuslink.module.messaging.domain.model.Conversation;
import com.campuslink.module.messaging.domain.model.ConversationSummary;
import com.campuslink.module.messaging.domain.model.Message;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessagingApplicationServiceTest {

    private static final long ME = 1L;
    private static final long PEER = 8L;

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private AccountApplicationService accountApplicationService;
    @Mock
    private UserBlockApplicationService userBlockApplicationService;
    @Mock
    private ModerationApplicationService moderationApplicationService;
    @InjectMocks
    private MessagingApplicationService service;

    @Test
    @DisplayName("发给自己 → 400 / 2015，且不查目标、不触达拉黑 / 机审 / 仓储")
    void sendToSelfIsRejected() {
        assertThatThrownBy(() -> service.send(ME, ME, "hi"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.CANNOT_MESSAGE_SELF));

        verifyNoInteractions(accountApplicationService, userBlockApplicationService,
                moderationApplicationService, conversationRepository, messageRepository);
    }

    @Test
    @DisplayName("目标不存在或已注销 → 404 / 2007（与主页同口径）")
    void sendToMissingTargetIsNotFound() {
        when(accountApplicationService.publicAccountOf(PEER))
                .thenThrow(new ApiException(ResultCode.USER_NOT_FOUND));

        assertThatThrownBy(() -> service.send(ME, PEER, "hi"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.USER_NOT_FOUND));

        verifyNoInteractions(conversationRepository, messageRepository);
    }

    @Test
    @DisplayName("发起人拉黑目标 → 403 / 4002，不继续查反向关系")
    void sendBlockedBySenderIsForbidden() {
        givenActivePeer();
        when(userBlockApplicationService.isBlocked(ME, PEER)).thenReturn(true);

        assertThatThrownBy(() -> service.send(ME, PEER, "hi"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.FORBIDDEN));

        verify(userBlockApplicationService, never()).isBlocked(PEER, ME);
        verifyNoInteractions(moderationApplicationService, conversationRepository, messageRepository);
    }

    @Test
    @DisplayName("目标拉黑发起人 → 同样 403 / 4002（双向拉黑，不提示被拉黑）")
    void sendBlockedByPeerIsForbidden() {
        givenActivePeer();
        when(userBlockApplicationService.isBlocked(ME, PEER)).thenReturn(false);
        when(userBlockApplicationService.isBlocked(PEER, ME)).thenReturn(true);

        assertThatThrownBy(() -> service.send(ME, PEER, "hi"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.FORBIDDEN));

        verifyNoInteractions(moderationApplicationService, conversationRepository, messageRepository);
    }

    @Test
    @DisplayName("机审命中 → 422 / 5001，且不建会话、不落消息")
    void sendModerationBlocked() {
        givenActivePeerAndNotBlocked();
        doThrow(new ApiException(ResultCode.MODERATION_BLOCKED))
                .when(moderationApplicationService).ensureAllowed("违禁内容");

        assertThatThrownBy(() -> service.send(ME, PEER, "违禁内容"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.MODERATION_BLOCKED));

        verifyNoInteractions(conversationRepository, messageRepository);
    }

    @Test
    @DisplayName("机审网关异常 → 503 / 5002（fail-closed，不静默放行）")
    void sendModerationUnavailable() {
        givenActivePeerAndNotBlocked();
        doThrow(new ApiException(ResultCode.MODERATION_UNAVAILABLE))
                .when(moderationApplicationService).ensureAllowed("hello");

        assertThatThrownBy(() -> service.send(ME, PEER, "hello"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.MODERATION_UNAVAILABLE));

        verifyNoInteractions(conversationRepository, messageRepository);
    }

    @Test
    @DisplayName("全空白内容 → 400 / 1001，不触达机审与仓储")
    void blankContentIsInvalid() {
        givenActivePeerAndNotBlocked();

        assertThatThrownBy(() -> service.send(ME, PEER, "   \n  "))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));

        verifyNoInteractions(moderationApplicationService, conversationRepository, messageRepository);
    }

    @Test
    @DisplayName("超过 1000 字 → 400 / 1001")
    void tooLongContentIsInvalid() {
        givenActivePeerAndNotBlocked();

        assertThatThrownBy(() -> service.send(ME, PEER, "a".repeat(1001)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));

        verifyNoInteractions(conversationRepository, messageRepository);
    }

    @Test
    @DisplayName("首次发送：建会话（归一 id）、落 strip 后内容、回写 last_message，返回消息出参")
    void sendCreatesConversationAndUpdatesLastMessage() {
        givenActivePeerAndNotBlocked();
        when(conversationRepository.findByPair(ME, PEER)).thenReturn(Optional.empty());
        when(conversationRepository.create(ME, PEER))
                .thenReturn(Conversation.rehydrate(77L, ME, PEER, null, null, null));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message draft = invocation.getArgument(0);
            return Message.rehydrate(101L, draft.getConversationId(), draft.getSenderId(),
                    draft.getReceiverId(), draft.getContent(), draft.getCreatedAt(), null);
        });

        MessageItem item = service.send(ME, PEER, "  hello\nworld  ");

        assertThat(item.id()).isEqualTo(101L);
        assertThat(item.senderId()).isEqualTo(ME);
        assertThat(item.receiverId()).isEqualTo(PEER);
        assertThat(item.content()).isEqualTo("hello\nworld");
        assertThat(item.createdAt()).isNotNull();

        ArgumentCaptor<Message> saved = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(saved.capture());
        assertThat(saved.getValue().getConversationId()).isEqualTo(77L);
        assertThat(saved.getValue().getContent()).isEqualTo("hello\nworld");
        verify(moderationApplicationService).ensureAllowed("hello\nworld");
        verify(conversationRepository).updateLastMessage(77L, 101L, item.createdAt());
    }

    @Test
    @DisplayName("已有会话：复用而不重建；id 归一（发送者较大时 pair 仍为 min/max）")
    void sendReusesExistingConversationWithNormalizedPair() {
        // 本例发送者是较大的 id（8），目标是 1：目标校验取 1，拉黑判定按 (8,1) 再 (1,8)
        when(accountApplicationService.publicAccountOf(ME)).thenReturn(mock(Account.class));
        when(userBlockApplicationService.isBlocked(PEER, ME)).thenReturn(false);
        when(userBlockApplicationService.isBlocked(ME, PEER)).thenReturn(false);
        when(conversationRepository.findByPair(ME, PEER))
                .thenReturn(Optional.of(Conversation.rehydrate(55L, ME, PEER, 9L, Instant.now(), Instant.now())));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message draft = invocation.getArgument(0);
            return Message.rehydrate(102L, draft.getConversationId(), draft.getSenderId(),
                    draft.getReceiverId(), draft.getContent(), draft.getCreatedAt(), null);
        });

        MessageItem item = service.send(PEER, ME, "hi");

        assertThat(item.senderId()).isEqualTo(PEER);
        assertThat(item.receiverId()).isEqualTo(ME);
        verify(conversationRepository, never()).create(anyLong(), anyLong());
        verify(conversationRepository).updateLastMessage(55L, 102L, item.createdAt());
    }

    @Test
    @DisplayName("会话列表：分页归一（page=0 / size=999 → 1 / 100），条目逐字段映射")
    void listConversationsNormalizesPagingAndMapsItems() {
        Instant at = Instant.parse("2026-10-24T12:00:00Z");
        when(conversationRepository.countOfUser(ME)).thenReturn(3L);
        when(conversationRepository.pageOfUser(ME, 0, 100))
                .thenReturn(List.of(new ConversationSummary(55L, PEER, "模拟学生08", "https://a.png",
                        "最后一条", at, 4L)));

        ConversationPage result = service.listConversations(ME, 0, 999);

        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(100);
        assertThat(result.total()).isEqualTo(3L);
        assertThat(result.list()).hasSize(1);
        assertThat(result.list().get(0).peerId()).isEqualTo(PEER);
        assertThat(result.list().get(0).peerNickname()).isEqualTo("模拟学生08");
        assertThat(result.list().get(0).unreadCount()).isEqualTo(4L);
        assertThat(result.list().get(0).lastMessagePreview()).isEqualTo("最后一条");
    }

    @Test
    @DisplayName("会话列表：size 非正数回落默认 20；第 2 页 offset = size")
    void listConversationsDefaultsSizeAndComputesOffset() {
        when(conversationRepository.countOfUser(ME)).thenReturn(0L);
        when(conversationRepository.pageOfUser(ME, 20, 20)).thenReturn(List.of());

        ConversationPage result = service.listConversations(ME, 2, 0);

        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.list()).isEmpty();
    }

    @Test
    @DisplayName("消息分页：无会话返回空页（不查消息表、不报错）")
    void listMessagesWithoutConversationReturnsEmptyPage() {
        when(conversationRepository.findByPair(ME, PEER)).thenReturn(Optional.empty());

        MessagePage result = service.listMessages(ME, PEER, 1, 20);

        assertThat(result.list()).isEmpty();
        assertThat(result.total()).isZero();
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(20);
        verifyNoInteractions(messageRepository);
    }

    @Test
    @DisplayName("消息分页：会话内倒序出参（仓储顺序原样透出），total 与页同源")
    void listMessagesScopedToConversation() {
        Instant now = Instant.parse("2026-10-24T12:00:00Z");
        when(conversationRepository.findByPair(ME, PEER))
                .thenReturn(Optional.of(Conversation.rehydrate(55L, ME, PEER, null, null, null)));
        when(messageRepository.countByConversation(55L)).thenReturn(2L);
        when(messageRepository.pageByConversation(55L, 0, 20)).thenReturn(List.of(
                Message.rehydrate(2L, 55L, PEER, ME, "第二条", now, null),
                Message.rehydrate(1L, 55L, ME, PEER, "第一条", now.minusSeconds(60), now)));

        MessagePage result = service.listMessages(ME, PEER, 1, 20);

        assertThat(result.total()).isEqualTo(2L);
        assertThat(result.list()).extracting(MessageItem::id).containsExactly(2L, 1L);
        assertThat(result.list().get(0).content()).isEqualTo("第二条");
    }

    @Test
    @DisplayName("自己与自己查消息：直接空页，不触达仓储")
    void listMessagesWithSelfIsEmpty() {
        MessagePage result = service.listMessages(ME, ME, 1, 20);

        assertThat(result.list()).isEmpty();
        assertThat(result.total()).isZero();
        verifyNoInteractions(conversationRepository, messageRepository);
    }

    @Test
    @DisplayName("标记已读：无会话返回 0（幂等，不更新）")
    void markReadWithoutConversationReturnsZero() {
        when(conversationRepository.findByPair(ME, PEER)).thenReturn(Optional.empty());

        MarkReadResult result = service.markRead(ME, PEER);

        assertThat(result.updated()).isZero();
        verifyNoInteractions(messageRepository);
    }

    @Test
    @DisplayName("标记已读：只更新 receiver=我 的未读行，返回本次条数")
    void markReadUpdatesReceiverOnly() {
        when(conversationRepository.findByPair(ME, PEER))
                .thenReturn(Optional.of(Conversation.rehydrate(55L, ME, PEER, null, null, null)));
        when(messageRepository.markRead(eq(55L), eq(ME), any(Instant.class))).thenReturn(2);

        MarkReadResult result = service.markRead(ME, PEER);

        assertThat(result.updated()).isEqualTo(2);
        verify(messageRepository).markRead(eq(55L), eq(ME), any(Instant.class));
    }

    @Test
    @DisplayName("自己与自己标记已读：返回 0，不触达仓储")
    void markReadWithSelfIsZero() {
        assertThat(service.markRead(ME, ME).updated()).isZero();

        verifyNoInteractions(conversationRepository, messageRepository);
    }

    @Test
    @DisplayName("未读总数直接取仓储计数")
    void unreadCountDelegatesToRepository() {
        when(messageRepository.countUnread(ME)).thenReturn(5L);

        UnreadCount result = service.unreadCount(ME);

        assertThat(result.unreadCount()).isEqualTo(5L);
    }

    private void givenActivePeer() {
        when(accountApplicationService.publicAccountOf(PEER)).thenReturn(mock(Account.class));
    }

    private void givenActivePeerAndNotBlocked() {
        givenActivePeer();
        when(userBlockApplicationService.isBlocked(ME, PEER)).thenReturn(false);
        when(userBlockApplicationService.isBlocked(PEER, ME)).thenReturn(false);
    }
}
