package com.campuslink.module.forum.domain.model;

import com.campuslink.module.forum.domain.exception.CannotAcceptOwnReplyException;
import com.campuslink.module.forum.domain.exception.NotAQuestionException;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/**
 * 帖子聚合根。contentHtml 在**发布时**由 application 调用 MarkdownRenderer 渲染后传入并落库，
 * 请求时零渲染（ADR-005）——聚合自身不感知 Markdown 实现。
 *
 * <p>CR-074 增量：viewCount（详情读取去重计数，走 {@code PostRepository#incrementViewCount} 定向 SQL，
 * 不进聚合写路径）与 coverUrl（发帖时从正文提取首张 https 图，一次性写入）进入聚合——
 * 二者随 rehydrate 重建、随 acceptReply 拷贝保留；favoriteCount 等仍未建模（无用例整行回写）。
 */
@Getter
public class Post {

    private final Long id;
    private final Long boardId;
    private final Long authorId;
    private final BoardType type;
    private final String title;
    private final String contentMd;
    private final String contentHtml;
    private final PostStatus status;
    private final int replyCount;
    private final int likeCount;
    private final int viewCount;
    private final String coverUrl;
    private final List<String> tags;
    private final boolean accepted;
    private final Long acceptedReplyId;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Post(Long id, Long boardId, Long authorId, BoardType type, String title,
                 String contentMd, String contentHtml, PostStatus status, int replyCount, int likeCount,
                 int viewCount, String coverUrl, List<String> tags, boolean accepted, Long acceptedReplyId,
                 Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.boardId = boardId;
        this.authorId = authorId;
        this.type = type;
        this.title = title;
        this.contentMd = contentMd;
        this.contentHtml = contentHtml;
        this.status = status;
        this.replyCount = replyCount;
        this.likeCount = likeCount;
        this.viewCount = viewCount;
        this.coverUrl = coverUrl;
        this.tags = tags;
        this.accepted = accepted;
        this.acceptedReplyId = acceptedReplyId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** 发布新帖：type 随版块，status 恒为 PUBLISHED，计数归零（id / 时间戳由数据库生成）；标题去除首尾空白；封面为发布时提取的首图（可为 null）；tags 为已规范化的标签名（可为空列表） */
    public static Post publish(Long boardId, Long authorId, BoardType type, String title,
                               String contentMd, String contentHtml, String coverUrl, List<String> tags) {
        return new Post(null, boardId, authorId, type, title.trim(), contentMd, contentHtml,
                PostStatus.PUBLISHED, 0, 0, 0, coverUrl, List.copyOf(tags), false, null, null, null);
    }

    /** 仓储重建入口（infrastructure 适配器调用）；已删除的行由仓储读侧过滤，不会到达这里 */
    public static Post rehydrate(Long id, Long boardId, Long authorId, BoardType type, String title,
                                 String contentMd, String contentHtml, PostStatus status, int replyCount,
                                 int likeCount, int viewCount, String coverUrl, List<String> tags, boolean accepted, Long acceptedReplyId,
                                 Instant createdAt, Instant updatedAt) {
        return new Post(id, boardId, authorId, type, title, contentMd, contentHtml, status,
                replyCount, likeCount, viewCount, coverUrl, tags, accepted, acceptedReplyId, createdAt, updatedAt);
    }

    /**
     * @deprecated 测试夹具过渡重载（CR-074）：等价于 viewCount=0、coverUrl=null、tags=空；
     * 既有单测不关心这三个读侧展示字段，逐个改写夹具文件收益为零——新代码一律用 18 参签名。
     */
    @Deprecated
    public static Post rehydrate(Long id, Long boardId, Long authorId, BoardType type, String title,
                                 String contentMd, String contentHtml, PostStatus status, int replyCount,
                                 int likeCount, boolean accepted, Long acceptedReplyId,
                                 Instant createdAt, Instant updatedAt) {
        return rehydrate(id, boardId, authorId, type, title, contentMd, contentHtml, status,
                replyCount, likeCount, 0, null, List.of(), accepted, acceptedReplyId, createdAt, updatedAt);
    }

    /** 对外可见性：仅 PUBLISHED 可读 / 可回帖（已删除属仓储读侧约定，见 PostRepository） */
    public boolean isVisible() {
        return status == PostStatus.PUBLISHED;
    }

    /**
     * 作者编辑自己的帖子（F-FORUM-007 / CR-128）：只改标题、正文与派生列（contentHtml / coverUrl / tags），
     * **版块、类型、状态、互动计数一律不动**——版块变更会让历史链接与列表归属漂移，另开 CR 才做。
     *
     * <p>与 {@link #acceptReply} 同为不可变写法：调用方拿新聚合，旧聚合不被就地改写。
     * 标题在此 trim（与 {@link #publish} 同口径）；正文不做 trim，Markdown 的前导空行可能是有意的。
     */
    public Post revise(String title, String contentMd, String contentHtml, String coverUrl, List<String> tags) {
        return new Post(id, boardId, authorId, type, title.trim(), contentMd, contentHtml, status,
                replyCount, likeCount, viewCount, coverUrl, List.copyOf(tags), accepted, acceptedReplyId,
                createdAt, updatedAt);
    }

    /**
     * 采纳最佳答案（F-QA-001）的领域规则：仅问答帖（QUESTION）可采纳；提问者不能采纳自己的回复。
     * 可更换（后一次覆盖前一次）与回复归属校验归 application（后者是仓储读得到的跨聚合事实）。
     */
    public Post acceptReply(long replyId, long replyAuthorId) {
        if (type != BoardType.QUESTION) {
            throw new NotAQuestionException();
        }
        if (authorId != null && authorId == replyAuthorId) {
            throw new CannotAcceptOwnReplyException();
        }
        return new Post(id, boardId, authorId, type, title, contentMd, contentHtml, status,
                replyCount, likeCount, viewCount, coverUrl, tags, true, replyId, createdAt, updatedAt);
    }
}
