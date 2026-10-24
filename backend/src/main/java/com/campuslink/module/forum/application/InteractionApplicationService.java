package com.campuslink.module.forum.application;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.account.application.UserBlockApplicationService;
import com.campuslink.module.forum.application.cmd.ForumResults.InteractionResult;
import com.campuslink.module.forum.domain.exception.PostNotFoundException;
import com.campuslink.module.forum.domain.gateway.FavoriteRepository;
import com.campuslink.module.forum.domain.gateway.LikeRepository;
import com.campuslink.module.forum.domain.gateway.PostRepository;
import com.campuslink.module.forum.domain.gateway.ReplyRepository;
import com.campuslink.module.forum.domain.model.LikeTargetType;
import com.campuslink.module.forum.domain.model.Post;
import com.campuslink.module.forum.domain.model.Reply;
import com.campuslink.module.notification.application.NotificationApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 论坛互动写用例（F-FORUM-005）：点赞（帖子 / 楼层）与收藏的 toggle。
 *
 * <p>目标存在性核验在本层（复用"不可读统一 404、不区分原因"口径）；去重由 likes / favorites
 * 主键唯一约束兜底——并发双击时后到者撞键走删除分支，不会重复计数；toggle 行与计数增减同事务。
 *
 * <p>互动通知（F-SOC-001）只在本次 toggle 结果为"生效"时发出——取消点赞不该撤回已送达的通知，
 * 通知与互动行同事务写入；"自己点赞自己的帖子"由 notification 侧统一抑制，此处不重复判断。
 */
@Service
@RequiredArgsConstructor
public class InteractionApplicationService {

    private final PostRepository postRepository;
    private final ReplyRepository replyRepository;
    private final LikeRepository likeRepository;
    private final FavoriteRepository favoriteRepository;
    private final NotificationApplicationService notificationService;
    private final UserBlockApplicationService userBlockApplicationService;

    @Transactional
    public InteractionResult togglePostLike(long userId, long postId) {
        Post post = requireVisiblePost(postId);
        assertNotBlockedBy(post.getAuthorId(), userId);
        boolean active = likeRepository.toggle(userId, LikeTargetType.POST, postId);
        int count = postRepository.adjustLikeCount(postId, active ? 1 : -1);
        if (active) {
            notificationService.postLiked(userId, postId, post.getAuthorId());
        }
        return new InteractionResult(active, count);
    }

    @Transactional
    public InteractionResult toggleReplyLike(long userId, long postId, long replyId) {
        Post post = requireVisiblePost(postId);
        // 回复不存在、不属于路径指定的帖子、或已下架 / 已删除，统一 404（CR-060：可见性与列表同源）
        Reply reply = replyRepository.findVisibleById(replyId)
                .filter(candidate -> candidate.getPostId().equals(post.getId()))
                .orElseThrow(PostNotFoundException::new);
        assertNotBlockedBy(reply.getAuthorId(), userId);
        boolean active = likeRepository.toggle(userId, LikeTargetType.REPLY, reply.getId());
        int count = replyRepository.adjustLikeCount(reply.getId(), active ? 1 : -1);
        if (active) {
            notificationService.replyLiked(userId, reply.getId(), reply.getAuthorId());
        }
        return new InteractionResult(active, count);
    }

    @Transactional
    public InteractionResult togglePostFavorite(long userId, long postId) {
        Post post = requireVisiblePost(postId);
        assertNotBlockedBy(post.getAuthorId(), userId);
        boolean active = favoriteRepository.toggle(userId, postId);
        int count = postRepository.adjustFavoriteCount(postId, active ? 1 : -1);
        if (active) {
            notificationService.postFavorited(userId, postId, post.getAuthorId());
        }
        return new InteractionResult(active, count);
    }

    private Post requireVisiblePost(Long postId) {
        return postRepository.findById(postId)
                .filter(Post::isVisible)
                .orElseThrow(PostNotFoundException::new);
    }

    /** 拉黑阻断（CR-129）：目标作者拉黑了行为者 → 互动统一 403，不提示"你被拉黑" */
    private void assertNotBlockedBy(long authorId, long actorId) {
        if (userBlockApplicationService.isBlocked(authorId, actorId)) {
            throw new ApiException(ResultCode.FORBIDDEN);
        }
    }
}
