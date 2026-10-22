package com.campuslink.module.notification.application;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.account.application.AccountApplicationService;
import com.campuslink.module.forum.application.ForumQueryApplicationService;
import com.campuslink.module.forum.application.cmd.ForumResults.PostBrief;
import com.campuslink.module.forum.application.cmd.ForumResults.ReplyBrief;
import com.campuslink.module.notification.application.cmd.NotificationResults.MarkAllReadResult;
import com.campuslink.module.notification.application.cmd.NotificationResults.NotificationItem;
import com.campuslink.module.notification.application.cmd.NotificationResults.UnreadCountResult;
import com.campuslink.module.notification.application.event.NotificationCreatedEvent;
import com.campuslink.module.notification.domain.gateway.NotificationRepository;
import com.campuslink.module.notification.domain.gateway.PageResult;
import com.campuslink.module.notification.domain.model.Notification;
import com.campuslink.module.notification.domain.model.NotificationTargetType;
import com.campuslink.module.notification.domain.model.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 通知用例（F-SOC-001）：触发侧自触发抑制、读侧跨上下文组装（昵称 / 标题 / 楼层号）与回落文案、
 * 分页与未读过滤归仓储、全部已读回显新标记条数、单条已读的幂等与归属判定。
 */
@ExtendWith(MockitoExtension.class)
class NotificationApplicationServiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-14T08:00:00Z");
    private static final long RECIPIENT = 42L;
    private static final long NOTIFICATION_ID = 5L;
    /** 另一个已登录账号：用于验证"他人通知"分支 */
    private static final long SOMEONE_ELSE = 999L;

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private AccountApplicationService accountApplicationService;
    @Mock
    private ForumQueryApplicationService forumQueryService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private NotificationApplicationService service;

    @Test
    @DisplayName("触发：落一条事实四元组，type / target 由入口决定，接收人是内容作者")
    void triggerSavesFactTuple() {
        service.postReplied(7L, 11L, RECIPIENT);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(NotificationType.REPLY);
        assertThat(saved.getTargetType()).isEqualTo(NotificationTargetType.REPLY);
        assertThat(saved.getTargetId()).isEqualTo(11L);
        assertThat(saved.getActorId()).isEqualTo(7L);
        assertThat(saved.getUserId()).isEqualTo(RECIPIENT);
        assertThat(saved.isRead()).isFalse();
    }

    @Test
    @DisplayName("实时流信号：通知落库后发布接收人事件（未读数由监听器在事务提交后自取）")
    void triggerPublishesNotificationCreatedEvent() {
        service.postReplied(7L, 11L, RECIPIENT);

        ArgumentCaptor<NotificationCreatedEvent> captor = ArgumentCaptor.forClass(NotificationCreatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(RECIPIENT);
    }

    @Test
    @DisplayName("自触发抑制：触发人即接收人时不产生通知（6 个入口共用同一兜底）")
    void selfTriggerIsSuppressed() {
        service.postReplied(7L, 11L, 7L);
        service.postLiked(7L, 9L, 7L);
        service.replyLiked(7L, 11L, 7L);
        service.postFavorited(7L, 9L, 7L);
        service.replyQuoted(7L, 11L, 7L);

        verify(notificationRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    /** CR-085 / F-009：引用通知与 REPLY 同构——目标是引用方的新楼层，接收人是被引用楼层的作者 */
    @Test
    @DisplayName("触发：replyQuoted 落 type=quote 的事实四元组，目标是引用方新楼层")
    void replyQuotedSavesQuoteNotification() {
        service.replyQuoted(7L, 456L, RECIPIENT);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(NotificationType.QUOTE);
        assertThat(saved.getTargetType()).isEqualTo(NotificationTargetType.REPLY);
        assertThat(saved.getTargetId()).isEqualTo(456L);
        assertThat(saved.getActorId()).isEqualTo(7L);
        assertThat(saved.getUserId()).isEqualTo(RECIPIENT);
        assertThat(saved.isRead()).isFalse();
    }

    @Test
    @DisplayName("列表读时组装：楼层类通知解析出所属帖子、标题与楼层号，帖子类直接用 targetId")
    void listAssemblesContentAcrossContexts() {
        when(notificationRepository.findPage(RECIPIENT, null, 1, 20)).thenReturn(new PageResult<>(
                List.of(notification(1L, NotificationType.REPLY, NotificationTargetType.REPLY, 11L, false),
                        notification(2L, NotificationType.LIKE, NotificationTargetType.POST, 9L, true)),
                2, 1, 20));
        when(accountApplicationService.nicknamesOf(anyCollection()))
                .thenReturn(Map.of(7L, "小李", 8L, "小王"));
        when(forumQueryService.replyBriefsOf(Set.of(11L))).thenReturn(Map.of(11L, new ReplyBrief(9L, 3)));
        when(forumQueryService.postBriefsOf(Set.of(9L))).thenReturn(Map.of(9L, new PostBrief("Spring 事务失效")));

        PageResult<NotificationItem> result = service.list(RECIPIENT, null, 1, 20);

        assertThat(result.total()).isEqualTo(2);
        NotificationItem replyItem = result.items().get(0);
        assertThat(replyItem.type()).isEqualTo("reply");
        assertThat(replyItem.actorNickname()).isEqualTo("小李");
        assertThat(replyItem.postId()).isEqualTo(9L);
        assertThat(replyItem.postTitle()).isEqualTo("Spring 事务失效");
        assertThat(replyItem.floorNo()).isEqualTo(3);
        assertThat(replyItem.read()).isFalse();

        NotificationItem postItem = result.items().get(1);
        assertThat(postItem.type()).isEqualTo("like");
        assertThat(postItem.postId()).isEqualTo(9L);
        assertThat(postItem.floorNo()).isNull();
        assertThat(postItem.read()).isTrue();
        // 一页只有一次批量查，不做 N+1
        verify(accountApplicationService).nicknamesOf(anyCollection());
        verify(forumQueryService).postBriefsOf(anyCollection());
    }

    @Test
    @DisplayName("回落文案：触发人取不到昵称显示已注销，目标帖子已删除显示内容已删除且不编造 postId")
    void listFallsBackWhenReferencedDataIsGone() {
        when(notificationRepository.findPage(RECIPIENT, null, 1, 20)).thenReturn(new PageResult<>(
                List.of(notification(1L, NotificationType.REPLY, NotificationTargetType.REPLY, 11L, false)),
                1, 1, 20));
        when(accountApplicationService.nicknamesOf(anyCollection())).thenReturn(Map.of());
        when(forumQueryService.replyBriefsOf(Set.of(11L))).thenReturn(Map.of());
        when(forumQueryService.postBriefsOf(Set.of())).thenReturn(Map.of());

        NotificationItem item = service.list(RECIPIENT, null, 1, 20).items().get(0);

        assertThat(item.actorNickname()).isEqualTo("已注销用户");
        assertThat(item.postTitle()).isEqualTo("内容已删除");
        assertThat(item.postId()).isNull();
        assertThat(item.floorNo()).isNull();
        assertThat(item.targetId()).isEqualTo(11L);
    }

    /**
     * CR-066 补的真实缺陷回归：目标本身是帖子时，旧实现的 {@code postId} 只看 targetId、不看摘要是否命中，
     * 于是"帖子被下架 / 被作者删除"后通知里仍下发可点链接，点进去 404 / 3001；而前端一直是按
     * "服务端不下发 postId 就不给跳转"实现的（那句注释写了很久、服务端从未履行）。
     * 现在标题回落与不给跳转是**同一个判定**的两个出口。
     */
    @Test
    @DisplayName("帖子类通知指向已下架 / 已删帖子：摘要未命中时 postId 一并置 null（前端据此不给跳转）")
    void postTargetNotificationDropsPostIdWhenBriefMissing() {
        when(notificationRepository.findPage(RECIPIENT, null, 1, 20)).thenReturn(new PageResult<>(
                List.of(notification(1L, NotificationType.LIKE, NotificationTargetType.POST, 9L, false)),
                1, 1, 20));
        when(accountApplicationService.nicknamesOf(anyCollection())).thenReturn(Map.of(7L, "小李"));
        when(forumQueryService.replyBriefsOf(Set.of())).thenReturn(Map.of());
        when(forumQueryService.postBriefsOf(Set.of(9L))).thenReturn(Map.of());

        NotificationItem item = service.list(RECIPIENT, null, 1, 20).items().get(0);

        assertThat(item.postTitle()).isEqualTo("内容已删除");
        assertThat(item.postId()).isNull();
        // targetId 仍在：条目本身可追溯，只是不给跳转目标
        assertThat(item.targetId()).isEqualTo(9L);
    }

    @Test
    @DisplayName("帖子类通知摘要命中时 postId 与标题照常下发（回落只在该条未命中时发生）")
    void postTargetNotificationKeepsPostIdWhenBriefPresent() {
        when(notificationRepository.findPage(RECIPIENT, null, 1, 20)).thenReturn(new PageResult<>(
                List.of(notification(1L, NotificationType.LIKE, NotificationTargetType.POST, 9L, false)),
                1, 1, 20));
        when(accountApplicationService.nicknamesOf(anyCollection())).thenReturn(Map.of(7L, "小李"));
        when(forumQueryService.replyBriefsOf(Set.of())).thenReturn(Map.of());
        when(forumQueryService.postBriefsOf(Set.of(9L))).thenReturn(Map.of(9L, new PostBrief("在架帖子")));

        NotificationItem item = service.list(RECIPIENT, null, 1, 20).items().get(0);

        assertThat(item.postId()).isEqualTo(9L);
        assertThat(item.postTitle()).isEqualTo("在架帖子");
    }

    @Test
    @DisplayName("空页短路：没有通知就不去查 account 与 forum")
    void emptyPageSkipsCrossContextReads() {
        when(notificationRepository.findPage(RECIPIENT, true, 1, 20))
                .thenReturn(new PageResult<>(List.of(), 0, 1, 20));

        assertThat(service.list(RECIPIENT, true, 1, 20).items()).isEmpty();
        verifyNoInteractions(accountApplicationService, forumQueryService);
    }

    @Test
    @DisplayName("未读过滤条件原样下推仓储；size 越界归一到默认 20 / 上限 100，page 归一到 1")
    void unreadFilterPassesThroughAndPaginationIsNormalized() {
        when(notificationRepository.findPage(anyLong(), any(), anyInt(), anyInt()))
                .thenReturn(new PageResult<>(List.of(), 0, 1, 20));

        service.list(RECIPIENT, false, 0, 5000);
        verify(notificationRepository).findPage(RECIPIENT, false, 1, 100);

        service.list(RECIPIENT, null, 1, -1);
        verify(notificationRepository).findPage(RECIPIENT, null, 1, 20);
    }

    @Test
    @DisplayName("未读数与全部已读：直接回显仓储计数 / 新标记条数")
    void unreadCountAndMarkAllReadEchoRepository() {
        when(notificationRepository.countUnread(RECIPIENT)).thenReturn(5L);
        when(notificationRepository.markAllRead(RECIPIENT)).thenReturn(5);

        assertThat(service.unreadCount(RECIPIENT)).isEqualTo(new UnreadCountResult(5L));
        assertThat(service.markAllRead(RECIPIENT)).isEqualTo(new MarkAllReadResult(5));
    }

    @Test
    @DisplayName("单条已读：本人的未读通知被标记，UPDATE 连同接收人一起下推仓储")
    void markReadFlagsOwnUnreadNotification() {
        when(notificationRepository.findById(NOTIFICATION_ID))
                .thenReturn(Optional.of(ownedBy(RECIPIENT, false)));

        service.markRead(NOTIFICATION_ID, RECIPIENT);

        verify(notificationRepository).markRead(NOTIFICATION_ID, RECIPIENT);
    }

    /**
     * 幂等不是"重复写一次也无所谓"，而是**一条 SQL 都不发**：前端是乐观更新 + fire-and-forget，
     * 同一条通知被点两次（或刷新后重点）是常态，此时再走 UPDATE 只会白写一次库。
     */
    @Test
    @DisplayName("单条已读幂等：已读通知再调不报错也不再发 UPDATE")
    void markReadIsIdempotentWhenAlreadyRead() {
        when(notificationRepository.findById(NOTIFICATION_ID))
                .thenReturn(Optional.of(ownedBy(RECIPIENT, true)));

        service.markRead(NOTIFICATION_ID, RECIPIENT);

        verify(notificationRepository, never()).markRead(anyLong(), anyLong());
    }

    @Test
    @DisplayName("通知不存在 → 3001，且不触达 UPDATE")
    void markReadOfMissingNotificationIsNotFound() {
        when(notificationRepository.findById(NOTIFICATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(NOTIFICATION_ID, RECIPIENT))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_FOUND));
        verify(notificationRepository, never()).markRead(anyLong(), anyLong());
    }

    /**
     * 他人的通知给 404 而不是 403：403 会向任意已登录账号确认"这个 id 上确实有一条通知"，
     * 而他人通知的存在性本身也是隐私。本用例守的是"两种情形不区分"这一条：改成 403 会让它变红。
     */
    @Test
    @DisplayName("通知属于他人 → 与不存在同一个 3001（不泄露存在性），且绝不改到别人的行")
    void markReadOfSomeoneElsesNotificationIsNotFound() {
        when(notificationRepository.findById(NOTIFICATION_ID))
                .thenReturn(Optional.of(ownedBy(SOMEONE_ELSE, false)));

        assertThatThrownBy(() -> service.markRead(NOTIFICATION_ID, RECIPIENT))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_FOUND));
        verify(notificationRepository, never()).markRead(anyLong(), anyLong());
    }

    private static Notification notification(Long id, NotificationType type, NotificationTargetType targetType,
            Long targetId, boolean read) {
        return Notification.rehydrate(id, RECIPIENT, type, 7L, targetType, targetId, read, CREATED_AT);
    }

    /** 单条已读用：接收人可变（归属分支必须能造出"别人的通知"），其余字段固定为楼层回复类 */
    private static Notification ownedBy(long recipientId, boolean read) {
        return Notification.rehydrate(NOTIFICATION_ID, recipientId, NotificationType.REPLY, 7L,
                NotificationTargetType.REPLY, 11L, read, CREATED_AT);
    }
}
