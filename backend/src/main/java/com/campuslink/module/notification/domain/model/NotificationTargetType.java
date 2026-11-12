package com.campuslink.module.notification.domain.model;

/**
 * 通知目标类型。存值与 {@code likes.target_type} / {@code reports.target_type} 同词表（大写枚举名），
 * 与 {@link NotificationType} 的小写词表分属两列。
 */
public enum NotificationTargetType {

    POST,
    REPLY,
    /** 平台直接面向用户的通知（警告 / 禁言，CR-209）：targetId 是接收人 userId，无帖子跳转 */
    USER;

    public static NotificationTargetType fromName(String name) {
        for (NotificationTargetType type : values()) {
            if (type.name().equals(name)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知通知目标类型: " + name);
    }
}
