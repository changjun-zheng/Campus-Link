package com.campuslink.module.forum.domain.model;

import java.time.Instant;

/**
 * 相似帖子推荐窄读载体（CR-077）：仅包含推荐条目需要的列，避免加载完整 {@link Post} 聚合。
 *
 * <p>为什么用独立载体（先例同 {@link MyReplyRow}）：
 * ① 推荐条目不需要 contentMd / contentHtml / status / likeCount 等字段，SELECT * 再丢弃是纯浪费；
 * ② 应用层据此直接构造出参，无需走 {@code toSummaries()} 的昵称 / 版块名 / 摘要渲染三件套。
 */
public record SimilarPostRow(Long id,
                             Long boardId,
                             Long authorId,
                             String title,
                             int replyCount,
                             boolean accepted,
                             Instant createdAt) {
}
