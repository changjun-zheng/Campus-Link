package com.campuslink.module.forum.application;

import com.campuslink.common.audit.AuditService;
import com.campuslink.common.exception.ApiException;
import com.campuslink.common.markdown.MarkdownRenderer;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.account.application.AccountApplicationService;
import com.campuslink.module.forum.application.cmd.ForumResults.PublishedPost;
import com.campuslink.module.forum.application.cmd.PublishPostCommand;
import com.campuslink.module.forum.application.cmd.UpdatePostCommand;
import com.campuslink.module.forum.domain.exception.BoardNotFoundException;
import com.campuslink.module.forum.domain.exception.PostNotFoundException;
import com.campuslink.module.forum.domain.gateway.BoardRepository;
import com.campuslink.module.forum.domain.gateway.ModerationGateway;
import com.campuslink.module.forum.domain.gateway.PostRepository;
import com.campuslink.module.forum.domain.gateway.ReplyRepository;
import com.campuslink.module.forum.domain.gateway.TagRepository;
import com.campuslink.module.forum.domain.model.Board;
import com.campuslink.module.forum.domain.model.Post;
import com.campuslink.module.forum.domain.model.Reply;
import com.campuslink.module.forum.domain.service.CoverImages;
import com.campuslink.module.forum.domain.service.TagNames;
import com.campuslink.module.notification.application.NotificationApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
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
    private final TagRepository tagRepository;

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
     * 帖子 type 随版块（QUESTION 版块即提问帖）。标签（CR-083）规范化后随帖落 tags / post_tags /
     * posts.tags 冗余列——三条写路径同事务，回滚即整体回滚。
     */
    @Transactional
    public PublishedPost publish(Long authorId, PublishPostCommand command) {
        accountApplicationService.assertActiveForContent(authorId);
        Board board = boardRepository.findByCode(command.boardCode())
                .orElseThrow(BoardNotFoundException::new);
        // 机审放在版块校验之后：无效请求（版块不存在）先给出准确错误，而不是被 5002 抢占语义。
        // 标题、正文、标签是三处独立的用户公开文本，必须各审一次——漏掉任一处都是可绕过的出口
        // （CR-124 补标题、CR-125 补标签；标签规范化不抛业务错误，放在机审前不改变错误优先级）
        List<String> tags = TagNames.normalize(command.tags(), 5);
        moderateOrThrow(command.title());
        moderateOrThrow(command.contentMd());
        for (String tag : tags) {
            moderateOrThrow(tag);
        }
        // 封面（CR-074 外链方案）：发布时从正文提取首张 https 图一次落库，读时零成本；无图即 null
        Post post = Post.publish(board.getId(), authorId, command.anonymous(), board.getType(), command.title(),
                command.contentMd(), markdownRenderer.render(command.contentMd()),
                CoverImages.firstHttpsImage(command.contentMd()), tags);
        Post saved = postRepository.save(post);
        if (!tags.isEmpty()) {
            tagRepository.attachToPost(saved.getId(), tags);
        }
        return new PublishedPost(saved.getId());
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
     * <p>
     * 可见性核验复用与读侧同一条门槛（不存在 / 已删除 / 非 PUBLISHED → 3001，不区分原因、防按 id 探测）；
     * 非作者 → 4002。墓碑行以下，前台所有位置自动不可见：列表 / 搜索 / 热榜候选在 SQL 侧过滤 {@code is_deleted}，
     * 详情 / 楼层 / 回帖 / 点赞 / 收藏都以 {@code requireVisiblePost} 开头，通知摘要查不到标题时回落
     * "内容已删除"——故本用例不碰 {@code replies} / {@code likes} /
     * {@code favorites}（它们是删除后的独立事实，
     * 处置台与统计要用）。
     *
     * <p>
     * 删除与审计同事务：PRD 明写"删除操作留后台审计日志"，二者必须同生同灭。
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

    /**
     * 作者编辑自己的帖子（F-FORUM-007 / CR-128）：标题 / 正文 / 标签可改，**版块不可改**。
     *
     * <p>
     * 可见性核验与删除同一条门槛（不存在 / 已删除 / 已下架 → 3001，不区分原因、防按 id 探测）；
     * 非作者 → 4002。机审口径与发帖逐字相同：标题、正文、每条标签各审一次（漏一处就是一个可绕过出口），
     * contentHtml / coverUrl 与发帖同源重算，标签走 replaceForPost 整组替换。
     *
     * <p>
     * 写入条件带 {@code is_deleted=0 AND status='PUBLISHED'}：读取后到写入之间若被并发下架 / 删除，
     * UPDATE 影响 0 行 → 3001，不把一次"看不见的写"当成成功；审计与写入同事务。
     */
    @Transactional
    public void revisePost(Long authorId, long postId, UpdatePostCommand command) {
        accountApplicationService.assertActiveForContent(authorId);
        Post post = postRepository.findById(postId)
                .filter(Post::isVisible)
                .orElseThrow(PostNotFoundException::new);
        if (!post.getAuthorId().equals(authorId)) {
            // 资源级授权归业务代码（框架只区分登录 / 未登录，CR-031 口径）
            throw new ApiException(ResultCode.FORBIDDEN);
        }
        List<String> tags = TagNames.normalize(command.tags(), 5);
        moderateOrThrow(command.title());
        moderateOrThrow(command.contentMd());
        for (String tag : tags) {
            moderateOrThrow(tag);
        }
        Post revised = post.revise(command.title(), command.contentMd(),
                markdownRenderer.render(command.contentMd()),
                CoverImages.firstHttpsImage(command.contentMd()), tags);
        if (!postRepository.revise(postId, revised.getTitle(), revised.getContentMd(),
                revised.getContentHtml(), revised.getCoverUrl(), tags)) {
            throw new PostNotFoundException();
        }
        tagRepository.replaceForPost(postId, tags);
        auditService.record(authorId, "POST_UPDATE", "posts", postId, auditDetail(post));
    }
}
