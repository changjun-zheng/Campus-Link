package com.campuslink.module.forum.application;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.markdown.MarkdownRenderer;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.account.application.AccountApplicationService;
import com.campuslink.module.forum.application.cmd.ForumResults.PublishedReply;
import com.campuslink.module.forum.application.cmd.PublishReplyCommand;
import com.campuslink.module.forum.domain.exception.PostNotFoundException;
import com.campuslink.module.forum.domain.gateway.ModerationGateway;
import com.campuslink.module.forum.domain.gateway.PostRepository;
import com.campuslink.module.forum.domain.gateway.ReplyRepository;
import com.campuslink.module.forum.domain.model.Post;
import com.campuslink.module.forum.domain.model.Reply;
import com.campuslink.module.notification.application.NotificationApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 论坛写用例：回帖（含楼层号分配，本 Sprint 唯一并发点） */
@Service
@RequiredArgsConstructor
public class ReplyApplicationService {

    private final PostRepository postRepository;
    private final ReplyRepository replyRepository;
    private final MarkdownRenderer markdownRenderer;
    private final NotificationApplicationService notificationService;
    private final ModerationGateway moderationGateway;
    private final AccountApplicationService accountApplicationService;

    /**
     * 回帖：楼层号与 {@code reply_count} 由**同一次递增**派生——{@code incrementReplyCountAndGet} 的
     * UPDATE 行锁先行，使同一帖子的并发回帖被串行化，floor_no 不会重号（设计 §4.1，D-1 口径：
     * 楼层 = 回复序号，第一条回复为 1 楼，帖子本体不占楼层号）。
     *
     * <p>计数自增与 INSERT 必须在**同一事务**内：否则计数已加而回复未落库时，楼层号与楼层数不再一致。
     */
    @Transactional
    public PublishedReply reply(Long postId, Long authorId, PublishReplyCommand command) {
        accountApplicationService.assertActiveForContent(authorId);
        Post post = postRepository.findById(postId)
                .filter(Post::isVisible)
                .orElseThrow(PostNotFoundException::new);
        // 机审放在帖子可见性校验之后：不可回帖的目标先给出准确的 3001，而不是被 5002 抢占语义
        moderateOrThrow(command.contentMd());
        int floorNo = postRepository.incrementReplyCountAndGet(post.getId());
        Reply reply = Reply.post(post.getId(), authorId, floorNo, command.contentMd(),
                markdownRenderer.render(command.contentMd()));
        Long replyId = replyRepository.save(reply).getId();
        // 通知与回帖同事务写入（F-SOC-001）；自问自答不产生通知，抑制逻辑归 notification 侧统一兜底
        notificationService.postReplied(authorId, replyId, post.getAuthorId());
        return new PublishedReply(replyId, floorNo);
    }

    /**
     * 机审前置（F-SAFE-001 / CR-082）：命中风险 → 5001 拦发布；网关任何异常 → 5002（fail-closed，
     * 宁可暂时关发布入口也不放行——技术方案 §7.3）。
     */
    private void moderateOrThrow(String content) {
        try {
            if (!moderationGateway.moderate(content).pass()) {
                throw new ApiException(ResultCode.MODERATION_BLOCKED);
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(ResultCode.MODERATION_UNAVAILABLE);
        }
    }
}
