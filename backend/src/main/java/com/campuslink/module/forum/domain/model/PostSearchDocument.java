package com.campuslink.module.forum.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * 搜索索引文档（CR-181）：只承载检索需要的窄字段，不含 contentHtml / 计数等展示列。
 *
 * <p>{@code visible} 为 false 表示该行已被删除或下架——增量同步据此把 id 从索引里删除，
 * 而不是写入一条"不可见文档"（索引里只保留可搜内容，读侧无需再按状态过滤）。
 */
public record PostSearchDocument(
        long id,
        String title,
        String content,
        List<String> tags,
        long boardId,
        long authorId,
        boolean anonymous,
        Instant createdAt,
        Instant updatedAt,
        boolean visible) {
}
