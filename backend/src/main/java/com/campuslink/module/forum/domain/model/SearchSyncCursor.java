package com.campuslink.module.forum.domain.model;

import java.time.Instant;

/**
 * 搜索索引增量同步游标（CR-181）：{@code (updated_at, id)} 复合位置。
 *
 * <p>只靠 updated_at 会漏掉同一秒内的多行（DATETIME 秒级精度），故用 id 做同秒内的次级游标；
 * 查询条件是 {@code updated_at > ? OR (updated_at = ? AND id > ?)}。
 */
public record SearchSyncCursor(Instant updatedAt, long id) {
}
