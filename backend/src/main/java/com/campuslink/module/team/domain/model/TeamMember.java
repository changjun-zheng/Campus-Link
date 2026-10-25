package com.campuslink.module.team.domain.model;

import lombok.Getter;

import java.time.Instant;

/**
 * 组队成员 / 申请实体（F-TEAM-001 / CR-137）。
 *
 * <p>发起人在建队时即写一条 {@code OWNER / ACCEPTED}；其他人先写 {@code MEMBER / PENDING}，
 * 由发起人审批后转 {@code ACCEPTED} 或 {@code REJECTED}。被拒后不可再次申请
 * （联合主键 + 状态判定，见应用层）。
 */
@Getter
public class TeamMember {

    private final long teamId;
    private final long userId;
    private final TeamMemberRole role;
    private TeamMemberStatus status;
    private final Instant appliedAt;
    private Instant decidedAt;

    private TeamMember(long teamId, long userId, TeamMemberRole role, TeamMemberStatus status,
                       Instant appliedAt, Instant decidedAt) {
        this.teamId = teamId;
        this.userId = userId;
        this.role = role;
        this.status = status;
        this.appliedAt = appliedAt;
        this.decidedAt = decidedAt;
    }

    /** 发起人：建队即成员，不占审批流程 */
    public static TeamMember owner(long teamId, long userId, Instant now) {
        return new TeamMember(teamId, userId, TeamMemberRole.OWNER, TeamMemberStatus.ACCEPTED, now, now);
    }

    /** 普通申请：待审批 */
    public static TeamMember application(long teamId, long userId, Instant now) {
        return new TeamMember(teamId, userId, TeamMemberRole.MEMBER, TeamMemberStatus.PENDING, now, null);
    }

    /** 仓储重建入口 */
    public static TeamMember rehydrate(long teamId, long userId, TeamMemberRole role,
                                       TeamMemberStatus status, Instant appliedAt, Instant decidedAt) {
        return new TeamMember(teamId, userId, role, status, appliedAt, decidedAt);
    }

    public boolean isPending() {
        return status == TeamMemberStatus.PENDING;
    }

    public boolean isAccepted() {
        return status == TeamMemberStatus.ACCEPTED;
    }

    /** 审批通过：只允许 PENDING → ACCEPTED */
    public void accept(Instant now) {
        if (!isPending()) {
            throw new IllegalStateException("只有待审批申请可以接受");
        }
        this.status = TeamMemberStatus.ACCEPTED;
        this.decidedAt = now;
    }

    /** 审批拒绝：只允许 PENDING → REJECTED */
    public void reject(Instant now) {
        if (!isPending()) {
            throw new IllegalStateException("只有待审批申请可以拒绝");
        }
        this.status = TeamMemberStatus.REJECTED;
        this.decidedAt = now;
    }
}
