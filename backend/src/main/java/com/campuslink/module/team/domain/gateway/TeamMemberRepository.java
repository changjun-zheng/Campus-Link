package com.campuslink.module.team.domain.gateway;

import com.campuslink.module.team.domain.model.TeamApplicationItem;
import com.campuslink.module.team.domain.model.TeamMember;
import com.campuslink.module.team.domain.model.TeamMemberProfile;
import com.campuslink.module.team.domain.model.TeamMemberStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 出站端口：组队成员与申请（CR-137）。
 *
 * <p>{@link #tryAddApplication} 返回 {@code false} 表示"该用户对这支组队已有关系"
 * （联合主键兜住并发），由应用层翻成业务码 6004；不向上抛数据库异常。
 */
public interface TeamMemberRepository {

    /** 写发起人关系（OWNER / ACCEPTED） */
    void save(TeamMember member);

    /** 新增申请；同一 (team,user) 已存在时返回 false，不抛 DuplicateKeyException */
    boolean tryAddApplication(long teamId, long userId, Instant appliedAt);

    Optional<TeamMember> findByTeamAndUser(long teamId, long userId);

    long countAccepted(long teamId);

    /** 详情页成员（ACCEPTED，含 OWNER）：昵称 / 头像来自 users 联表，已注销昵称回落占位 */
    List<TeamMemberProfile> acceptedProfiles(long teamId);

    /** 发起人待审批列表（只列 PENDING，按申请时间升序） */
    List<TeamApplicationItem> pendingApplications(long teamId, int offset, int limit);

    long countPending(long teamId);

    /** 审批结果迁移；条件带 {@code status='PENDING'}，返回受影响行数供调用方判并发 */
    int updateStatus(long teamId, long userId, TeamMemberStatus status, Instant decidedAt);
}
