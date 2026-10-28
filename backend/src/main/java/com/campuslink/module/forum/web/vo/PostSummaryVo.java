package com.campuslink.module.forum.web.vo;

import com.campuslink.module.forum.application.cmd.ForumResults.PostSummary;

import java.time.Instant;

/** 帖子列表项视图（设计 §3.2）：summary 已是服务端去 Markdown 后的纯文本，前端不再兜底截断；accepted 供 [已采纳] 徽标（F-QA-001）；viewCount / coverUrl 供阅读数与封面缩略图（CR-074）；likedByMe 供列表内点赞回显，口径同 PostDetailVo——登录为真实状态、匿名恒 false（CR-080） */
public record PostSummaryVo(Long id, String boardCode, String boardName, String title, String authorNickname,
                            String authorAvatarUrl,
                            int replyCount, int likeCount, int viewCount, String coverUrl, java.util.List<String> tags,
                            String summary, Instant createdAt, boolean accepted, boolean likedByMe,
                            boolean anonymous) {

    public static PostSummaryVo from(PostSummary summary) {
        return new PostSummaryVo(summary.id(), summary.boardCode(), summary.boardName(), summary.title(),
                summary.authorNickname(), summary.authorAvatarUrl(), summary.replyCount(), summary.likeCount(),
                summary.viewCount(), summary.coverUrl(), summary.tags(), summary.summary(), summary.createdAt(),
                summary.accepted(), summary.likedByMe(), summary.anonymous());
    }
}
