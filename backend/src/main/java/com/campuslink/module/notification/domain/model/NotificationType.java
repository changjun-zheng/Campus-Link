package com.campuslink.module.notification.domain.model;

/**
 * 通知类型（F-SOC-001）。{@code code} 是 {@code notifications.type} 列的存值，取值为小写词表
 * （V1 DDL 注释 {@code reply/like/favorite/accept/quote}），与 {@code likes.target_type} 的大写枚举名
 * 分属两套词表，不要混用。
 *
 * <p>{@code QUOTE}（回复被引用）自 CR-085（F-009）落地：词表是 V1 就预留的值，本轮兑现；
 * 通知目标是**引用方的新楼层**（与 REPLY 同构，落点永远是发生互动的新内容）。
 *
 * <p>{@code CONTENT_REMOVED / CONTENT_RESTORED}（内容被平台下架 / 恢复，F-SAFE-003 / CR-126）：
 * 目标是**被处置的帖子或楼层本身**，actor 是处置管理员、接收人是内容作者。{@code notifications.type}
 * 是自由字符串列（V1 DDL 只写了当时的词表注释），新增取值**零迁移**。
 */
public enum NotificationType {

    REPLY("reply"),
    LIKE("like"),
    FAVORITE("favorite"),
    ACCEPT("accept"),
    QUOTE("quote"),
    CONTENT_REMOVED("content_removed"),
    CONTENT_RESTORED("content_restored");

    private final String code;

    NotificationType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static NotificationType fromCode(String code) {
        for (NotificationType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知通知类型: " + code);
    }
}
