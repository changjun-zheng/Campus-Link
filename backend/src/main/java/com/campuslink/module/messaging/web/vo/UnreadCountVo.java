package com.campuslink.module.messaging.web.vo;

import com.campuslink.module.messaging.application.cmd.MessagingResults.UnreadCount;

/** 未读私信数（F-SOC-003 / CR-135）：顶栏角标 */
public record UnreadCountVo(long unreadCount) {

    public static UnreadCountVo from(UnreadCount result) {
        return new UnreadCountVo(result.unreadCount());
    }
}
