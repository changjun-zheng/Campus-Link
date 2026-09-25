package com.campuslink.module.forum.application.cmd;

import java.time.Instant;

/**
 * 论坛用例出参（Command 模式，与 account 的 {@code AccountCommands} 同规矩：入参 / 出参都是应用层的稳定接口）。
 *
 * <p>查询类出参不直接复用 web 的 VO：web 依赖 application，反向依赖会成环（ADR-012 四层单向）。
 * 写操作的两个出参只有 id，沿用 account 的 {@code RosterImportResult} 先例直接作为响应体。
 */
public final class ForumResults {

    private ForumResults() {
    }

    /** 列表项（设计 §3.2）：summary 为服务端去 Markdown 后的纯文本摘要；accepted 供列表 [已采纳] 徽标（F-QA-001）；viewCount / coverUrl / tags 供阅读数、封面缩略图与标签 chips（CR-074 / CR-083） */
    public record PostSummary(Long id, String boardCode, String boardName, String title, String authorNickname,
                              int replyCount, int likeCount, int viewCount, String coverUrl,
                              java.util.List<String> tags, String summary, Instant createdAt,
                              boolean accepted, boolean likedByMe) {
    }

    /** 详情（设计 §3.4）：contentHtml 是发布时渲染好的 HTML，前端直接渲染；authorId / boardType 供前端判定采纳按钮可见性（F-QA-001）；likedByMe / favoritedByMe 仅登录时填充、匿名恒 false（F-FORUM-005）；viewCount / tags 供阅读数与标签展示（CR-074 / CR-083） */
    public record PostDetail(Long id, String boardCode, String boardName, String boardType, String title,
                             String contentHtml, long authorId, String authorNickname,
                             int replyCount, int likeCount, int viewCount, java.util.List<String> tags, boolean accepted,
                             boolean likedByMe, boolean favoritedByMe,
                             Instant createdAt) {
    }

    /** 楼层项（设计 §3.5）：authorId / accepted 供前端渲染采纳按钮与已采纳标识（F-QA-001）；likeCount / likedByMe 供楼层点赞回显（F-FORUM-005），likedByMe 仅登录时填充 */
    public record ReplyItem(Long id, int floorNo, String contentHtml, long authorId,
                            String authorNickname, boolean accepted, int likeCount,
                            boolean likedByMe, Instant createdAt) {
    }

    /** 发帖结果：只回 id，前端据此跳详情（设计 §3.3） */
    public record PublishedPost(Long id) {
    }

    /**
     * "我的帖子"条目（F-ACC-007b）：**刻意不复用 {@link PostSummary}**——后者与全站列表 / 搜索共享，
     * 往里加一个 {@code status} 等于把"这条被平台下架了"广播给所有读者，正是本条要守住的隐私面。
     *
     * <p>条目只有「标题 + status + 时间 + 摘要」：<b>不给正文原文、不给可点跳转、不给下架原因</b>
     * （原因在 {@code audit_logs.detail}，属运营侧；给用户等于给申诉开无底洞）。
     */
    public record MyPostSummary(Long id, String boardCode, String title, String summary,
                               String status, Instant createdAt) {
    }

    /**
     * "我的回帖"条目（F-ACC-007c）：带父帖定位（{@code postId} + 标题）与**楼层自身**的 status；
     * 父帖不可见的楼层在仓储层就整条不出现，故这里出现的每一条都保证可点。
     * 不下发 {@code contentMd} 原文——本期无编辑能力，下发原文只是扩大出口。
     */
    public record MyReplyItem(Long id, Long postId, String postTitle, int floorNo, String summary,
                             String status, Instant createdAt) {
    }

    /** 回帖结果：floorNo 为本次分配的楼层号（设计 §3.6，口径见 sprint-2-design §4.1） */
    public record PublishedReply(Long id, int floorNo) {
    }

    /** 点赞 / 收藏 toggle 结果（F-FORUM-005）：active 为操作后的状态（true=已赞 / 已收藏），count 为操作后的最新计数 */
    public record InteractionResult(boolean active, int count) {
    }

    /**
     * 帖子只读摘要（F-SOC-001 通知读时组装用）：跨上下文出参必须留在 application 包
     * （守护测试 G4 禁止 notification 引 forum 的 domain / web 类型），故不复用 {@code Post} 聚合。
     * 已删除的帖子不出现在结果中，由调用方决定回落文案。
     */
    public record PostBrief(String title) {
    }

    /** 楼层只读摘要（同上）：postId 与 floorNo 供通知条目跳转定位 */
    public record ReplyBrief(Long postId, int floorNo) {
    }

    /**
     * 热榜刷新一轮的统计（ADR-006）：refreshed 为写回分数的帖子数、reset 为被置 0 的非候选帖子数。
     * 只供定时触发器记 1 行 INFO 摘要——定时任务没有请求可返回，日志是它唯一的可观测出口；**不进对外契约**。
     */
    public record HotRefreshResult(int refreshed, int reset, long elapsedMillis) {
    }

    /**
     * 相似帖子推荐条目（CR-077）：窄出参，只含判断“是否已有等价问题”所需的最小字段集。
     *
     * <p>刻意不复用 {@link PostSummary}——后者带昵称 / 摘要 / likeCount / viewCount / coverUrl，
     * 推荐场景不需要这些，调 {@code toSummaries()} 只是白白发起批量查询和 Markdown 渲染。
     */
    public record SimilarPostResult(Long id, String title, String boardCode, String boardName,
                                    int replyCount, boolean accepted, Instant createdAt) {
    }
}
