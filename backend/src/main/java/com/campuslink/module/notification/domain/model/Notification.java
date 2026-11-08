package com.campuslink.module.notification.domain.model;

import lombok.Getter;

import java.time.Instant;

/**
 * 站内通知（F-SOC-001）聚合根：一条"某人对你发布的内容做了某事"。
 *
 * <p>只存**事实四元组**（接收人 / 类型 / 触发人 / 目标），不存标题、昵称、楼层号等展示字段——
 * 这些在读时跨上下文组装（见 {@code NotificationApplicationService}），代价是通知内容跟随原文变化
 * （帖子改标题后通知显示新标题），换取 V1 既有表零结构变更；本产品的帖子无编辑功能，该代价当前不可见。
 *
 * <p>跳转所需的 {@code postId} 同样不落列：目标为 POST 时即 {@code targetId}，为 REPLY 时由 forum 侧读时解析。
 */
@Getter
public class Notification {

    private final Long id;
    private final Long userId;
    private final NotificationType type;
    private final Long actorId;
    private final NotificationTargetType targetType;
    private final Long targetId;
    /**
     * 平台通知正文（CR-209）：警告原因 / 禁言说明。互动通知没有可存储的正文（内容由目标读时组装），保持 null；
     * 这是对「只存事实四元组」口径的一次**有意例外**——警告原因不属于任何一处可回读的业务内容。
     */
    private final String message;
    private final boolean read;
    private final Instant createdAt;

    private Notification(Long id, Long userId, NotificationType type, Long actorId,
                         NotificationTargetType targetType, Long targetId, String message, boolean read,
                         Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.type = type;
        this.actorId = actorId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.message = message;
        this.read = read;
        this.createdAt = createdAt;
    }

    /** 新建通知：id 与时间戳由数据库生成，未读；互动通知正文为空 */
    public static Notification of(NotificationType type, long actorId, NotificationTargetType targetType,
                                  long targetId, long userId) {
        return of(type, actorId, targetType, targetId, userId, null);
    }

    /** 新建平台通知（CR-209）：正文随行落库 */
    public static Notification of(NotificationType type, long actorId, NotificationTargetType targetType,
                                  long targetId, long userId, String message) {
        return new Notification(null, userId, type, actorId, targetType, targetId, message, false, null);
    }

    /** 仓储重建入口（infrastructure 适配器调用）；保留旧签名，正文默认为空 */
    public static Notification rehydrate(Long id, Long userId, NotificationType type, Long actorId,
                                         NotificationTargetType targetType, Long targetId, boolean read,
                                         Instant createdAt) {
        return rehydrate(id, userId, type, actorId, targetType, targetId, null, read, createdAt);
    }

    /** 仓储重建入口（CR-209 起带正文） */
    public static Notification rehydrate(Long id, Long userId, NotificationType type, Long actorId,
                                         NotificationTargetType targetType, Long targetId, String message,
                                         boolean read, Instant createdAt) {
        return new Notification(id, userId, type, actorId, targetType, targetId, message, read, createdAt);
    }
}
