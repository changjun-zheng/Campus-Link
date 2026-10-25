package com.campuslink.module.team.domain.model;

import java.time.Instant;

/** 发起人侧的申请条目（CR-137）：审批列表只列 PENDING，为后续扩展保留状态字段 */
public record TeamApplicationItem(long userId,
                                  String nickname,
                                  String avatarUrl,
                                  TeamMemberStatus status,
                                  Instant appliedAt) {
}
