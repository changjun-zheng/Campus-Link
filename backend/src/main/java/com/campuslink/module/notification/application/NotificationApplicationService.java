package com.campuslink.module.notification.application;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.account.application.AccountApplicationService;
import com.campuslink.module.account.application.UserBlockApplicationService;
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
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 通知用例（F-SOC-001）：5 个触发入口 + 我的通知 / 未读数 / 全部已读 / 单条已读。
 *
 * <p><b>触发侧</b>由 forum 的 application 服务在动作成功后直接调用本服务（同事务写入）——落库仍走直调，
 * 不把"写通知"交给事件总线。落库后额外发一个 {@link NotificationCreatedEvent}，只供实时流
 * （F-010）在**事务提交后**推送未读角标；写与推互不阻塞。接收人由调用方给出：forum 侧当时已握有
 * 帖子 / 楼层聚合，再查一次只是重复读。<b>自触发抑制</b>在本层兜底（自己回帖 / 自己点赞自己的内容不打扰自己）。
 *
 * <p>
 * <b>读侧组装</b>：通知表只存事实四元组，昵称跨 account 取、帖子标题与楼层号跨 forum 取，
 * 三个 Map 各一页一次批量查（不做 N+1，口径同 {@code ForumQueryApplicationService}）。
 * 跨上下文只允许调用对方 {@code application}（ADR-012 / 守护测试 G4），故本类不出现 domain 类型。
 */
@Service
@RequiredArgsConstructor
public class NotificationApplicationService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    /** 触发人已注销时的回落文案（与 forum 列表口径一致） */
    private static final String NICKNAME_FALLBACK = "已注销用户";
    /** 目标帖子 / 楼层已删除时的回落文案 */
    private static final String CONTENT_FALLBACK = "内容已删除";

    private final NotificationRepository notificationRepository;
    private final AccountApplicationService accountApplicationService;
    private final UserBlockApplicationService userBlockApplicationService;
    private final ForumQueryApplicationService forumQueryService;
    private final ApplicationEventPublisher eventPublisher;

    /** 帖子被回复：接收人是帖子作者，目标是新楼层 */
    public void postReplied(long replierId, long replyId, long postAuthorId) {
        notify(NotificationType.REPLY, replierId, NotificationTargetType.REPLY, replyId, postAuthorId);
    }

    /**
     * 楼层被引用（F-009 / CR-085）：接收人是被引用楼层的作者，目标是**引用方的新楼层**——
     * 与 {@link #postReplied} 同构，通知落点永远是"发生互动的新内容"，接收人点进去看到的是引用自己的那条回复。
     * 自引用抑制沿用 {@code notify()} 既有兜底（引用自己的楼层不打扰自己）。
     */
    public void replyQuoted(long quoterId, long newReplyId, long quotedReplyAuthorId) {
        notify(NotificationType.QUOTE, quoterId, NotificationTargetType.REPLY, newReplyId, quotedReplyAuthorId);
    }

    /** 回复被采纳：接收人是被采纳楼层的作者，目标即该楼层 */
    public void replyAccepted(long askerId, long replyId, long replyAuthorId) {
        notify(NotificationType.ACCEPT, askerId, NotificationTargetType.REPLY, replyId, replyAuthorId);
    }

    /** 帖子被点赞 */
    public void postLiked(long likerId, long postId, long postAuthorId) {
        notify(NotificationType.LIKE, likerId, NotificationTargetType.POST, postId, postAuthorId);
    }

    /** 楼层被点赞 */
    public void replyLiked(long likerId, long replyId, long replyAuthorId) {
        notify(NotificationType.LIKE, likerId, NotificationTargetType.REPLY, replyId, replyAuthorId);
    }

    /** 帖子被收藏 */
    public void postFavorited(long favoriterId, long postId, long postAuthorId) {
        notify(NotificationType.FAVORITE, favoriterId, NotificationTargetType.POST, postId, postAuthorId);
    }

    /**
     * 帖子被平台下架（F-SAFE-003 / CR-126）：actor 是处置管理员、接收人是内容作者。
     * 自触发抑制沿用 {@code notify()} 兜底（管理员恰好是作者时不打扰自己）。
     *
     * <p>四个入口按"帖子 / 楼层 × 下架 / 恢复"拆开，而不是让 forum 侧传
     * {@link NotificationTargetType}：跨上下文只允许调用 application 服务，传对方 domain 类型
     * 会被 G4 拦下（ArchitectureGuardTest 实测）。
     */
    public void contentRemovedPost(long operatorId, long postId, long authorId) {
        notify(NotificationType.CONTENT_REMOVED, operatorId, NotificationTargetType.POST, postId, authorId);
    }

    /** 楼层被平台下架（F-SAFE-003 / CR-126） */
    public void contentRemovedReply(long operatorId, long replyId, long authorId) {
        notify(NotificationType.CONTENT_REMOVED, operatorId, NotificationTargetType.REPLY, replyId, authorId);
    }

    /** 帖子被平台恢复（F-SAFE-003 / CR-126） */
    public void contentRestoredPost(long operatorId, long postId, long authorId) {
        notify(NotificationType.CONTENT_RESTORED, operatorId, NotificationTargetType.POST, postId, authorId);
    }

    /** 楼层被平台恢复（F-SAFE-003 / CR-126） */
    public void contentRestoredReply(long operatorId, long replyId, long authorId) {
        notify(NotificationType.CONTENT_RESTORED, operatorId, NotificationTargetType.REPLY, replyId, authorId);
    }

    /** 我的通知分页；{@code unread} 为 null 不限、true 只未读、false 只已读 */
    public PageResult<NotificationItem> list(long userId, Boolean unread, int page, int size) {
        int currentPage = Math.max(page, 1);
        int pageSize = size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        PageResult<Notification> found = notificationRepository.findPage(userId, unread, currentPage, pageSize);
        List<Notification> rows = found.items();
        if (rows.isEmpty()) {
            return new PageResult<>(List.of(), found.total(), currentPage, pageSize);
        }

        Map<Long, String> nicknames = accountApplicationService.nicknamesOf(
                rows.stream().map(Notification::getActorId).toList());
        // 楼层类通知先解析所属帖子，再与帖子类通知合并成一次帖子标题批量查询
        Map<Long, ReplyBrief> replyBriefs = forumQueryService.replyBriefsOf(
                rows.stream().filter(n -> n.getTargetType() == NotificationTargetType.REPLY)
                        .map(Notification::getTargetId).collect(Collectors.toSet()));
        Set<Long> postIds = rows.stream()
                .map(n -> postIdOf(n, replyBriefs))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, PostBrief> postBriefs = forumQueryService.postBriefsOf(postIds);

        List<NotificationItem> items = rows.stream()
                .map(n -> toItem(n, nicknames, replyBriefs, postBriefs))
                .toList();
        return new PageResult<>(items, found.total(), currentPage, pageSize);
    }

    /** 未读数（顶栏角标） */
    public UnreadCountResult unreadCount(long userId) {
        return new UnreadCountResult(notificationRepository.countUnread(userId));
    }

    /** 全部已读：返回本次新标记的条数（无未读时为 0，不报错） */
    public MarkAllReadResult markAllRead(long userId) {
        return new MarkAllReadResult(notificationRepository.markAllRead(userId));
    }

    /**
     * 单条已读（幂等，F-SOC-001）。
     *
     * <p>
     * <b>不存在与"存在但属于他人"给同一个 404</b>：后者若回 403，就等于向任意已登录账号确认了
     * "这个 id 上确实有一条通知"——他人通知的存在性本身也是隐私，故不区分。
     *
     * <p>
     * <b>已读短路</b>：再调直接返回（HTTP 仍 200），不重复 UPDATE；前端是乐观更新 +
     * fire-and-forget，同一行被点两次是常态，不能因此报错。
     *
     * <p>
     * 不记审计：口径同 {@link #markAllRead}，标记自己的通知已读非安全敏感操作。
     */
    public void markRead(long notificationId, long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        if (!Objects.equals(notification.getUserId(), userId)) {
            throw new ApiException(ResultCode.NOT_FOUND);
        }
        if (notification.isRead()) {
            return;
        }
        notificationRepository.markRead(notificationId, userId);
    }

    private void notify(NotificationType type, long actorId, NotificationTargetType targetType,
            long targetId, long recipientId) {
        if (actorId == recipientId) {
            return;
        }
        // 拉黑抑制（CR-129）：接收人拉黑了触发者 → 不落库、不推送；解除后新互动恢复
        if (userBlockApplicationService.isBlocked(recipientId, actorId)) {
            return;
        }
        notificationRepository.save(Notification.of(type, actorId, targetType, targetId, recipientId));
        // 只给实时流一个信号；未读数由监听器在事务提交后自取
        eventPublisher.publishEvent(new NotificationCreatedEvent(recipientId));
    }

    /** 跳转目标帖子：目标本身是帖子即 targetId，是楼层则取楼层所属帖（楼层已删则 null） */
    private static Long postIdOf(Notification notification, Map<Long, ReplyBrief> replyBriefs) {
        if (notification.getTargetType() == NotificationTargetType.POST) {
            return notification.getTargetId();
        }
        ReplyBrief brief = replyBriefs.get(notification.getTargetId());
        return brief == null ? null : brief.postId();
    }

    /**
     * 组装单条通知。
     *
     * <p>
     * ⚠️ {@code postId} 在**帖子摘要未命中时也必须为 null**——CR-066 之前它只由 {@link #postIdOf} 决定，
     * 于是指向"已删帖 / 已下架帖"的帖子类通知仍下发可点链接，点进去落 404 / 3001；而前端
     * {@code NotificationsView.vue} 一直按"服务端不下发 postId 即不给跳转"实现（那句注释写了很久，
     * 服务端却从未真的履行）。现在两侧对齐，标题回落与不可点击是**同一个判定**的两个出口。
     */
    private static NotificationItem toItem(Notification notification, Map<Long, String> nicknames,
            Map<Long, ReplyBrief> replyBriefs, Map<Long, PostBrief> postBriefs) {
        Long resolvedPostId = postIdOf(notification, replyBriefs);
        PostBrief post = resolvedPostId == null ? null : postBriefs.get(resolvedPostId);
        ReplyBrief reply = notification.getTargetType() == NotificationTargetType.REPLY
                ? replyBriefs.get(notification.getTargetId())
                : null;
        return new NotificationItem(notification.getId(), notification.getType().code(),
                notification.getActorId(),
                nicknames.getOrDefault(notification.getActorId(), NICKNAME_FALLBACK),
                notification.getTargetType().name(), notification.getTargetId(),
                post == null ? null : resolvedPostId,
                post == null ? CONTENT_FALLBACK : post.title(),
                reply == null ? null : reply.floorNo(),
                notification.isRead(), notification.getCreatedAt());
    }
}
