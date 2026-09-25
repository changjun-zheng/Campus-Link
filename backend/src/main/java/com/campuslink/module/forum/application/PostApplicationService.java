package com.campuslink.module.forum.application;

import com.campuslink.common.audit.AuditService;
import com.campuslink.common.exception.ApiException;
import com.campuslink.common.markdown.MarkdownRenderer;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.account.application.AccountApplicationService;
import com.campuslink.module.forum.application.cmd.ForumResults.PublishedPost;
import com.campuslink.module.forum.application.cmd.PublishPostCommand;
import com.campuslink.module.forum.domain.exception.BoardNotFoundException;
import com.campuslink.module.forum.domain.exception.PostNotFoundException;
import com.campuslink.module.forum.domain.gateway.BoardRepository;
import com.campuslink.module.forum.domain.gateway.ModerationGateway;
import com.campuslink.module.forum.domain.gateway.PostRepository;
import com.campuslink.module.forum.domain.gateway.ReplyRepository;
import com.campuslink.module.forum.domain.model.Board;
import com.campuslink.module.forum.domain.model.Post;
import com.campuslink.module.forum.domain.model.Reply;
import com.campuslink.module.forum.domain.service.CoverImages;
import com.campuslink.module.notification.application.NotificationApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 论坛写用例：发帖、采纳最佳答案、作者删除自己的帖子 */
@Service
@RequiredArgsConstructor
public class PostApplicationService {

    /** 审计 detail 里的标题截断长度：审计要能看出"删了哪篇"，但不必复制整篇标题 */
    private static final int AUDIT_TITLE_MAX_CHARS = 100;

    private final BoardRepository boardRepository;
    private final PostRepository postRepository;
    private final ReplyRepository replyRepository;
    private final MarkdownRenderer markdownRenderer;
    private final NotificationApplicationService notificationService;
    private final AuditService auditService;
    private final ModerationGateway moderationGateway;
    private final AccountApplicationService accountApplicationService;

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

    /**
     * 发帖：{@code contentHtml} 在**发布时**渲染后随帖子一起落库，请求时零渲染（ADR-005）；
     * 帖子 type 随版块（QUESTION 版块即提问帖）。
     *
     * <p>单条 INSERT，无需事务编排；鉴权在 web 层（N-4 敞口下必须手写，见设计 §4.2）。
     */
    public PublishedPost publish(Long authorId, PublishPostCommand command) {
        accountApplicationService.assertActiveForContent(authorId);
        Board board = boardRepository.findByCode(command.boardCode())
                .orElseThrow(BoardNotFoundException::new);
        // 机审放在版块校验之后：无效请求（版块不存在）先给出准确错误，而不是被 5002 抢占语义
        moderateOrThrow(command.contentMd());
        // 封面（CR-074 外链方案）：发布时从正文提取首张 https 图一次落库，读时零成本；无图即 null
        Post post = Post.publish(board.getId(), authorId, board.getType(), command.title(),
                command.contentMd(), markdownRenderer.render(command.contentMd()),
                CoverImages.firstHttpsImage(command.contentMd()));
        return new PublishedPost(postRepository.save(post).getId());
    }

    /**
     * 采纳最佳答案（F-QA-001，仅提问者）：领域规则（问答帖 / 不能采纳自己回复）在 {@link Post#acceptReply}，
     * 跨聚合事实（回复存在且属于该帖）在应用层核验；落库走两个定向 UPDATE（posts 行锁先行，并发采纳串行化、
     * 后写覆盖前写即"可更换"），replies 的旧采纳标志清理与新标志置位同事务。
     */
    @Transactional
    public void acceptReply(Long askerId, long postId, long replyId) {
        Post post = postRepository.findById(postId)
                .filter(Post::isVisible)
                .orElseThrow(PostNotFoundException::new);
        if (!post.getAuthorId().equals(askerId)) {
            // 资源级授权归业务代码（框架只区分登录 / 未登录，CR-031 口径）
            throw new ApiException(ResultCode.FORBIDDEN);
        }
        Reply reply = replyRepository.findVisibleById(replyId)
                .filter(r -> r.getPostId().equals(post.getId()))
                .orElseThrow(PostNotFoundException::new);
        post.acceptReply(reply.getId(), reply.getAuthorId());
        postRepository.updateAcceptedReply(postId, replyId);
        replyRepository.updateAcceptedFlags(postId, replyId);
        // 采纳通知发给被采纳楼层的作者（F-SOC-001）；"不能采纳自己回复"已由领域规则拦下，此处不需再判自触发
        notificationService.replyAccepted(askerId, replyId, reply.getAuthorId());
    }

    /**
     * 作者删除自己的帖子（F-FORUM-006）：写 {@code is_deleted=1} 墓碑，**不物理删、不级联**。
     *
     * <p>可见性核验复用与读侧同一条门槛（不存在 / 已删除 / 非 PUBLISHED → 3001，不区分原因、防按 id 探测）；
     * 非作者 → 4002。墓碑行以下，前台所有位置自动不可见：列表 / 搜索 / 热榜候选在 SQL 侧过滤 {@code is_deleted}，
     * 详情 / 楼层 / 回帖 / 点赞 / 收藏都以 {@code requireVisiblePost} 开头，通知摘要查不到标题时回落
     * "内容已删除"——故本用例不碰 {@code replies} / {@code likes} / {@code favorites}（它们是删除后的独立事实，
     * 处置台与统计要用）。
     *
     * <p>删除与审计同事务：PRD 明写"删除操作留后台审计日志"，二者必须同生同灭。
     */
    @Transactional
    public void deletePost(Long operatorId, long postId) {
        Post post = postRepository.findById(postId)
                .filter(Post::isVisible)
                .orElseThrow(PostNotFoundException::new);
        if (!post.getAuthorId().equals(operatorId)) {
            // 资源级授权归业务代码：框架只判"有没有登录"，判不了"是不是作者的帖子"（CR-031 口径）
            throw new ApiException(ResultCode.FORBIDDEN);
        }
        if (!postRepository.markDeleted(postId)) {
            throw new PostNotFoundException();
        }
        auditService.record(operatorId, "POST_DELETE", "posts", postId, auditDetail(post));
    }

    /** 审计 detail 只带版块与截断标题：正文已随墓碑行留在库里，不在审计表再存一份长文本 */
    private static String auditDetail(Post post) {
        String title = post.getTitle();
        if (title != null && title.length() > AUDIT_TITLE_MAX_CHARS) {
            title = title.substring(0, AUDIT_TITLE_MAX_CHARS) + "…";
        }
        return "boardId=%s title=%s".formatted(post.getBoardId(), title);
    }
}
