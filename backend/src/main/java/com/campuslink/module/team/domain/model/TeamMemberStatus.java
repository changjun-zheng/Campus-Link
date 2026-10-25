package com.campuslink.module.team.domain.model;

/** 组队关系状态（CR-137）：待审批 / 已加入 / 已拒绝；被拒后不可再次申请 */
public enum TeamMemberStatus {
    PENDING,
    ACCEPTED,
    REJECTED
}
