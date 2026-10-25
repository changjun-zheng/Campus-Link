package com.campuslink.module.team.domain.model;

/** 组队状态（F-TEAM-001 / CR-137）：招募中 → 满员（自动）/ 已关闭（发起人）；关闭不可重开 */
public enum TeamStatus {

    RECRUITING,
    FULL,
    CLOSED;

    public static TeamStatus parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return RECRUITING;
        }
        try {
            return valueOf(raw.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
