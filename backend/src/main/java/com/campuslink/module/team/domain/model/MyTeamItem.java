package com.campuslink.module.team.domain.model;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 「我的组队」条目（CR-137）：我发起的（role=OWNER，带待审批数）或我申请的（role=MEMBER，带申请状态）。
 */
public record MyTeamItem(long id,
                         String title,
                         TeamDirection direction,
                         String target,
                         int capacity,
                         long acceptedCount,
                         TeamStatus status,
                         LocalDate deadline,
                         TeamMemberRole myRole,
                         TeamMemberStatus myStatus,
                         long pendingCount,
                         Instant createdAt) {
}
