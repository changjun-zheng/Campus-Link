package com.campuslink.module.notification.application.cmd;

import java.time.Instant;

/**
 * 通知用例出参（与 forum 的 {@code ForumResults}、account 的 {@code AccountCommands} 同规矩：
 * 入参 / 出参都是应用层的稳定接口，web 层不反向被依赖）。
 */
public final class NotificationResults {

    private NotificationResults() {
    }

    /**
     * 通知列表项：{@code type} 为小写词表（reply / like / favorite / accept），
     * {@code postId} 与 {@code postTitle} 为**读时组装**结果——**所属帖子不可见（作者已删的墓碑行、
     * 或被平台下架）时 {@code postId} 为 null、标题落 {@code 内容已删除}**，前端据此渲染成不可点击的条目
     * （零迁移的代价，见方案「实施结果」；{@code postId} 与标题同步置空由 CR-066 补齐，此前只回落标题、
     * 链接仍是死的 404）。
     *
     * <p>{@code floorNo} 仅目标为楼层时有值，供详情页锚定楼层；楼层自身被下架时仍给楼层号
     * （{@code ReplyRepository#findByIds} 刻意不过滤 status），但条目能否点击只取决于所属帖子。
     */
    public record NotificationItem(Long id, String type, Long actorId, String actorNickname,
                                   String actorAvatarUrl,
                                   String targetType, Long targetId,
                                   Long postId, String postTitle, Integer floorNo,
                                   boolean read, Instant createdAt) {
    }

    /** 未读数（顶栏角标）；沿用 forum 的先例——小出参直接作为响应体，不再另造 VO */
    public record UnreadCountResult(long unreadCount) {
    }

    /** 全部已读结果：{@code updated} 为本次新标记的条数，无未读时为 0（幂等，不报错） */
    public record MarkAllReadResult(int updated) {
    }
}
