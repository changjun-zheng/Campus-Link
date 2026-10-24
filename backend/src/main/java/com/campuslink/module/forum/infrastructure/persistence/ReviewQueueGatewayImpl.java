package com.campuslink.module.forum.infrastructure.persistence;

import com.campuslink.module.forum.domain.gateway.ReviewQueueGateway;
import com.campuslink.module.forum.infrastructure.persistence.mapper.ReviewQueueMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/** 适配器：ReviewQueueGateway 的 MyBatis-Plus 实现（F-SAFE-004 / CR-133） */
@Repository
@RequiredArgsConstructor
public class ReviewQueueGatewayImpl implements ReviewQueueGateway {

    private final ReviewQueueMapper reviewQueueMapper;

    @Override
    public List<ReviewItem> posts(int offset, int limit) {
        return reviewQueueMapper.posts(offset, limit).stream().map(ReviewQueueGatewayImpl::toItem).toList();
    }

    @Override
    public List<ReviewItem> replies(int offset, int limit) {
        return reviewQueueMapper.replies(offset, limit).stream().map(ReviewQueueGatewayImpl::toItem).toList();
    }

    @Override
    public long countPosts() {
        return reviewQueueMapper.countPosts();
    }

    @Override
    public long countReplies() {
        return reviewQueueMapper.countReplies();
    }

    private static ReviewItem toItem(ReviewItemRow row) {
        return new ReviewItem(row.getId(), row.getKind(), row.getTitle(), row.getBoardName(),
                row.getAuthorNickname(), row.getStatus(), row.getCreatedAt());
    }
}
