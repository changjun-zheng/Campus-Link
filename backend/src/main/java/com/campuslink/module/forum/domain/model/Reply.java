package com.campuslink.module.forum.domain.model;

import lombok.Getter;

import java.time.Instant;

/**
 * 楼层回复（独立聚合，经 post_id 关联帖子）。
 *
 * <p>楼层口径（设计 §4.1，D-1）：**floor_no = 回复序号**，第一条回复为 1 楼，帖子本体不占楼层号。
 * 楼层号不与帖子挂钩，由 application 通过 {@code PostRepository#incrementReplyCountAndGet} 分配后传入。
 *
 * <p>引用（F-009 / CR-085）：{@code quotedReplyId} 指向**同帖**的另一楼层，可空。目标只保证"发布当时
 * 可见且同帖"；落库后目标仍可能被下架 / 删除，故读侧渲染引用摘要时须对目标**再过一次可见门**
 * （{@code ReplyRepository#findVisibleByIds}，"内容不可见不外泄"口径），聚合不承载目标的实时状态。
 */
@Getter
public class Reply {

    private final Long id;
    private final Long postId;
    private final Long authorId;
    private final int floorNo;
    private final String contentMd;
    private final String contentHtml;
    private final Long quotedReplyId;
    private final boolean accepted;
    private final int likeCount;
    private final Instant createdAt;

    private Reply(Long id, Long postId, Long authorId, int floorNo, String contentMd,
                  String contentHtml, Long quotedReplyId, boolean accepted, int likeCount, Instant createdAt) {
        this.id = id;
        this.postId = postId;
        this.authorId = authorId;
        this.floorNo = floorNo;
        this.contentMd = contentMd;
        this.contentHtml = contentHtml;
        this.quotedReplyId = quotedReplyId;
        this.accepted = accepted;
        this.likeCount = likeCount;
        this.createdAt = createdAt;
    }

    /** 发布回复：floorNo 已由调用方分配（见类注释），contentHtml 已渲染，quotedReplyId 可空（F-009） */
    public static Reply post(Long postId, Long authorId, int floorNo, String contentMd, String contentHtml,
                             Long quotedReplyId) {
        return new Reply(null, postId, authorId, floorNo, contentMd, contentHtml, quotedReplyId, false, 0, null);
    }

    /** 仓储重建入口（infrastructure 适配器调用） */
    public static Reply rehydrate(Long id, Long postId, Long authorId, int floorNo, String contentMd,
                                  String contentHtml, Long quotedReplyId, boolean accepted, int likeCount,
                                  Instant createdAt) {
        return new Reply(id, postId, authorId, floorNo, contentMd, contentHtml, quotedReplyId, accepted,
                likeCount, createdAt);
    }
}
