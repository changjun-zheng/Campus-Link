package com.campuslink.module.forum.web.vo;

import com.campuslink.module.forum.application.cmd.ForumResults.PublicReplyItem;

import java.time.Instant;

/**
 * 他人主页「公开楼层」条目视图（CR-104）：{@code postId} + {@code postTitle} + {@code floorNo} 为父帖定位。
 *
 * <p>与 {@link MyReplyVo} 的**唯一**形状差别是没有 {@code status}：本人视角要把"我哪一层被下架"露给作者，
 * 路人视角则已在 SQL 侧把不可见楼层整条滤掉，下发这一列只会让每个匿名访客读到处置事实。
 *
 * <p>同样**不下发 {@code contentMd} 原文**——只给服务端截断后的 {@code summary}（120 字上限，与全站列表同源），
 * 本条链路上没有任何一处需要楼层全文。
 */
public record PublicReplyVo(Long id, Long postId, String postTitle, int floorNo, String summary,
                           Instant createdAt) {

    public static PublicReplyVo from(PublicReplyItem item) {
        return new PublicReplyVo(item.id(), item.postId(), item.postTitle(), item.floorNo(),
                item.summary(), item.createdAt());
    }
}
