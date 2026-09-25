package com.campuslink.module.forum.infrastructure.persistence;

import com.campuslink.module.forum.domain.model.BoardType;
import com.campuslink.module.forum.domain.model.Post;
import com.campuslink.module.forum.domain.model.PostStatus;

/**
 * 防腐层：posts 表 DO ↔ 帖子聚合互转。
 *
 * <p>{@code toDo} 只写聚合拥有的列；tags / favorite_count / accepted_reply_id / hot_score / is_deleted
 * 留在 DO 中为 null，MyBatis-Plus 默认策略（NOT_NULL）会略过它们，由数据库默认值兜底。
 */
public final class PostConverter {

    private PostConverter() {
    }

    public static PostDO toDo(Post post) {
        PostDO d = new PostDO();
        d.setId(post.getId());
        d.setBoardId(post.getBoardId());
        d.setAuthorId(post.getAuthorId());
        d.setType(post.getType().name());
        d.setTitle(post.getTitle());
        d.setContentMd(post.getContentMd());
        d.setContentHtml(post.getContentHtml());
        d.setStatus(post.getStatus().name());
        d.setReplyCount(post.getReplyCount());
        d.setLikeCount(post.getLikeCount());
        d.setViewCount(post.getViewCount());
        d.setCoverUrl(post.getCoverUrl());
        d.setTags(post.getTags() == null ? null : String.join(",", post.getTags()));
        d.setIsAccepted(post.isAccepted());
        d.setAcceptedReplyId(post.getAcceptedReplyId());
        d.setCreatedAt(post.getCreatedAt());
        d.setUpdatedAt(post.getUpdatedAt());
        return d;
    }

    public static Post toDomain(PostDO d) {
        return Post.rehydrate(d.getId(), d.getBoardId(), d.getAuthorId(), BoardType.valueOf(d.getType()),
                d.getTitle(), d.getContentMd(), d.getContentHtml(), PostStatus.valueOf(d.getStatus()),
                d.getReplyCount(), d.getLikeCount(),
                d.getViewCount() == null ? 0 : d.getViewCount(), d.getCoverUrl(),
                d.getTags() == null || d.getTags().isBlank() ? java.util.List.of() : java.util.List.of(d.getTags().split(",")),
                Boolean.TRUE.equals(d.getIsAccepted()),
                d.getAcceptedReplyId(), d.getCreatedAt(), d.getUpdatedAt());
    }
}
