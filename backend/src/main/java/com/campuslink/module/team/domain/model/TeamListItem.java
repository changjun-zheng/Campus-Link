package com.campuslink.module.team.domain.model;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 组队列表条目（F-TEAM-001 / CR-137）：广场列表与「我的组队」共用的只读投影来源。
 * 领域层只描述"要展示什么"，不含任何 SQL / 框架类型。
 */
public record TeamListItem(long id,
                           String title,
                           TeamDirection direction,
                           String target,
                           int capacity,
                           long acceptedCount,
                           TeamStatus status,
                           LocalDate deadline,
                           long ownerId,
                           String ownerNickname,
                           Instant createdAt) {
}
