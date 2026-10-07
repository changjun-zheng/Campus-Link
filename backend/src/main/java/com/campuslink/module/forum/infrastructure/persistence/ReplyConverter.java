package com.campuslink.module.forum.infrastructure.persistence;

import com.campuslink.module.forum.domain.model.Reply;

/** 防腐层：replies 表 DO ↔ 回复聚合互转（quoted_reply_id 自 CR-085 起随聚合读写，列在 V1 已预留） */
public final class ReplyConverter {

    private ReplyConverter() {
    }

    public static ReplyDO toDo(Reply reply) {
        ReplyDO d = new ReplyDO();
        d.setId(reply.getId());
        d.setPostId(reply.getPostId());
        d.setAuthorId(reply.getAuthorId());
        d.setFloorNo(reply.getFloorNo());
        d.setContentMd(reply.getContentMd());
        d.setContentHtml(reply.getContentHtml());
        d.setQuotedReplyId(reply.getQuotedReplyId());
        d.setIsAccepted(reply.isAccepted());
        d.setCreatedAt(reply.getCreatedAt());
        return d;
    }

    public static Reply toDomain(ReplyDO d) {
        return Reply.rehydrate(d.getId(), d.getPostId(), d.getAuthorId(), d.getFloorNo(),
                d.getContentMd(), d.getContentHtml(), d.getQuotedReplyId(),
                Boolean.TRUE.equals(d.getIsAccepted()),
                d.getLikeCount() == null ? 0 : d.getLikeCount(), d.getCreatedAt());
    }
}
