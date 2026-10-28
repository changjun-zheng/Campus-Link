package com.campuslink.module.forum.web.vo;

import com.campuslink.module.forum.application.cmd.ForumResults.QuotedBrief;
import com.campuslink.module.forum.application.cmd.ForumResults.ReplyItem;

import java.time.Instant;

/**
 * 楼层视图（设计 §3.5）：floorNo = 回复序号（第一条回复为 1 楼，帖子本体不占楼层号）；
 * authorId / accepted 供采纳按钮与已采纳标识（F-QA-001）；likeCount / likedByMe 供楼层点赞回显（F-FORUM-005），
 * likedByMe 仅登录请求时为真实状态、匿名恒 false。
 *
 * <p>{@code quoted} 为被引用楼层摘要（F-009 / CR-085）：无引用、或被引楼层已不可见
 * （下架 / 已删除）时为 null——null 即前端不渲染引用块，被处置的内容不从这里外泄。
 */
public record ReplyVo(Long id, int floorNo, String contentHtml, long authorId, String authorNickname,
                      String authorAvatarUrl, boolean accepted, int likeCount, boolean likedByMe,
                      QuotedReplyVo quoted, Instant createdAt) {

    /** 被引用楼层摘要（F-009 / CR-085）：excerpt 为服务端去 Markdown 后的 120 字纯文本，前端不二次处理 */
    public record QuotedReplyVo(long replyId, int floorNo, String authorNickname, String excerpt) {
    }

    public static ReplyVo from(ReplyItem item) {
        QuotedBrief brief = item.quoted();
        return new ReplyVo(item.id(), item.floorNo(), item.contentHtml(), item.authorId(),
                item.authorNickname(), item.authorAvatarUrl(), item.accepted(), item.likeCount(), item.likedByMe(),
                brief == null ? null
                        : new QuotedReplyVo(brief.replyId(), brief.floorNo(), brief.authorNickname(), brief.excerpt()),
                item.createdAt());
    }
}
