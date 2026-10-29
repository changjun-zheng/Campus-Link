package com.campuslink.module.notification.web.vo;

import com.campuslink.module.notification.application.cmd.NotificationResults.NotificationItem;

import java.time.Instant;

/** 通知列表项视图（F-SOC-001）：postId / postTitle / floorNo 为服务端读时组装结果，目标已删时 postId 为 null */
public record NotificationVo(Long id, String type, Long actorId, String actorNickname,
                             String actorAvatarUrl,
                             String targetType, Long targetId,
                             Long postId, String postTitle, Integer floorNo,
                             boolean read, Instant createdAt) {

    public static NotificationVo from(NotificationItem item) {
        return new NotificationVo(item.id(), item.type(), item.actorId(), item.actorNickname(),
                item.actorAvatarUrl(), item.targetType(), item.targetId(), item.postId(), item.postTitle(),
                item.floorNo(), item.read(), item.createdAt());
    }
}
