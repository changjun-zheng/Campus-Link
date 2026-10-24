package com.campuslink.module.messaging.application;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.account.application.AccountApplicationService;
import com.campuslink.module.account.application.UserBlockApplicationService;
import com.campuslink.module.forum.application.ModerationApplicationService;
import com.campuslink.module.messaging.application.cmd.MessagingResults.ConversationItem;
import com.campuslink.module.messaging.application.cmd.MessagingResults.ConversationPage;
import com.campuslink.module.messaging.application.cmd.MessagingResults.MarkReadResult;
import com.campuslink.module.messaging.application.cmd.MessagingResults.MessageItem;
import com.campuslink.module.messaging.application.cmd.MessagingResults.MessagePage;
import com.campuslink.module.messaging.application.cmd.MessagingResults.UnreadCount;
import com.campuslink.module.messaging.domain.gateway.ConversationRepository;
import com.campuslink.module.messaging.domain.gateway.MessageRepository;
import com.campuslink.module.messaging.domain.model.Conversation;
import com.campuslink.module.messaging.domain.model.Message;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 私信用例（F-SOC-003 / CR-135）：会话列表 / 消息分页 / 发送 / 已读 / 未读总数。
 *
 * <p>业务口径（方案 §2 / §7）：
 * ① 不能发给自己 → {@code 2015 / 400}；目标不存在或已注销 → {@code 2007 / 404}（与主页同口径，
 * 不给按 id 探测账号状态的口子）；
 * ② 双向拉黑（任一方向）→ {@code 4002 / 403}，不提示"你被拉黑"；历史消息仍可读（列表不过滤）；
 * ③ 内容过 forum 的机审入口 {@link ModerationApplicationService#ensureAllowed}，命中 {@code 5001 / 422}、
 * 网关异常 {@code 5002 / 503}；纯文本 1~1000 字，落库前 strip，不渲染 Markdown、不落 HTML；
 * ④ 分页归一：{@code page >= 1}、{@code 1 <= size <= 100}，会话与消息同口径。
 *
 * <p>跨上下文只调用对方 {@code application}（ADR-012 / 守护测试 G4）：账号存在性走 account，
 * 拉黑判定走 account，机审走 forum；本类不出现其他上下文的 domain 类型。
 */
@Service
@RequiredArgsConstructor
public class MessagingApplicationService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final AccountApplicationService accountApplicationService;
    private final UserBlockApplicationService userBlockApplicationService;
    private final ModerationApplicationService moderationApplicationService;

    /**
     * 发送私信：先做全部校验、再建会话 / 落消息 / 回写摘要（同一事务）。
     * 首次会话由唯一键兜底并发（{@link ConversationRepository#create} 撞键即回读）。
     */
    @Transactional
    public MessageItem send(long senderId, long peerId, String content) {
        if (senderId == peerId) {
            throw new ApiException(ResultCode.CANNOT_MESSAGE_SELF);
        }
        // 不存在与已注销统一 2007（publicAccountOf 的口径）；BANNED 不在拦截面内——封禁限的是发言与登录
        accountApplicationService.publicAccountOf(peerId);
        if (userBlockApplicationService.isBlocked(senderId, peerId)
                || userBlockApplicationService.isBlocked(peerId, senderId)) {
            throw new ApiException(ResultCode.FORBIDDEN);
        }
        String normalized = requireContent(content);
        moderationApplicationService.ensureAllowed(normalized);

        Conversation.Pair pair = Conversation.pairOf(senderId, peerId);
        Conversation conversation = conversationRepository.findByPair(pair.lowId(), pair.highId())
                .orElseGet(() -> conversationRepository.create(pair.lowId(), pair.highId()));
        Instant now = Instant.now();
        Message message = messageRepository.save(
                Message.compose(conversation.getId(), senderId, peerId, normalized, now));
        conversationRepository.updateLastMessage(conversation.getId(), message.getId(), message.getCreatedAt());
        return toItem(message);
    }

    /** 我的会话列表：按最后一条消息时间倒序；total 与页内容同 WHERE（仓储保证） */
    public ConversationPage listConversations(long userId, int page, int size) {
        int currentPage = normalizePage(page);
        int pageSize = normalizeSize(size);
        long total = conversationRepository.countOfUser(userId);
        List<ConversationItem> items = conversationRepository
                .pageOfUser(userId, offsetOf(currentPage, pageSize), pageSize).stream()
                .map(row -> new ConversationItem(row.peerId(), row.peerNickname(), row.peerAvatarUrl(),
                        row.lastMessagePreview(), row.lastMessageAt(), row.unreadCount()))
                .toList();
        return new ConversationPage(items, total, currentPage, pageSize);
    }

    /**
     * 与某人的消息分页（最新在前）。无会话返回空页而不是 404——"还没聊过"是正常状态，
     * 从别人的主页点「私信」进来时页面必须能直接写第一条。
     */
    public MessagePage listMessages(long viewerId, long peerId, int page, int size) {
        int currentPage = normalizePage(page);
        int pageSize = normalizeSize(size);
        if (viewerId == peerId) {
            return new MessagePage(List.of(), 0, currentPage, pageSize);
        }
        Conversation.Pair pair = Conversation.pairOf(viewerId, peerId);
        Conversation conversation = conversationRepository.findByPair(pair.lowId(), pair.highId()).orElse(null);
        if (conversation == null) {
            return new MessagePage(List.of(), 0, currentPage, pageSize);
        }
        long total = messageRepository.countByConversation(conversation.getId());
        List<MessageItem> items = messageRepository
                .pageByConversation(conversation.getId(), offsetOf(currentPage, pageSize), pageSize).stream()
                .map(MessagingApplicationService::toItem)
                .toList();
        return new MessagePage(items, total, currentPage, pageSize);
    }

    /** 把该会话中"接收人是我且未读"的消息置为已读；无会话 / 自己与自己均幂等返回 0 */
    public MarkReadResult markRead(long viewerId, long peerId) {
        if (viewerId == peerId) {
            return new MarkReadResult(0);
        }
        Conversation.Pair pair = Conversation.pairOf(viewerId, peerId);
        Conversation conversation = conversationRepository.findByPair(pair.lowId(), pair.highId()).orElse(null);
        if (conversation == null) {
            return new MarkReadResult(0);
        }
        return new MarkReadResult(messageRepository.markRead(conversation.getId(), viewerId, Instant.now()));
    }

    /** 我的未读私信总数（顶栏角标） */
    public UnreadCount unreadCount(long userId) {
        return new UnreadCount(messageRepository.countUnread(userId));
    }

    /** 纯文本 1~1000 字：strip 后判空（防全空白），长度以 strip 后计 */
    private static String requireContent(String content) {
        String normalized = content == null ? "" : content.strip();
        if (normalized.isEmpty() || normalized.length() > Message.MAX_CONTENT_LENGTH) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
        return normalized;
    }

    private static MessageItem toItem(Message message) {
        return new MessageItem(message.getId(), message.getSenderId(), message.getReceiverId(),
                message.getContent(), message.getCreatedAt(), message.getReadAt());
    }

    private static int normalizePage(int page) {
        return Math.max(page, 1);
    }

    /** size 缺省 / 非法一律回落默认值，上限 100（与通知 / 关注列表同口径） */
    private static int normalizeSize(int size) {
        return size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
    }

    /** 偏移量防溢出：page 由客户端传入，{@code (page-1)*size} 在极端值下会溢出 int */
    private static int offsetOf(int page, int size) {
        return (int) Math.min((long) (page - 1) * size, Integer.MAX_VALUE);
    }
}
