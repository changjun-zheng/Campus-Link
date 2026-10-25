package com.campuslink.module.team.infrastructure.persistence;

import com.campuslink.module.team.domain.gateway.TeamMemberRepository;
import com.campuslink.module.team.domain.model.TeamApplicationItem;
import com.campuslink.module.team.domain.model.TeamMember;
import com.campuslink.module.team.domain.model.TeamMemberProfile;
import com.campuslink.module.team.domain.model.TeamMemberRole;
import com.campuslink.module.team.domain.model.TeamMemberStatus;
import com.campuslink.module.team.infrastructure.persistence.mapper.TeamMemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 适配器：TeamMemberRepository 端口的 MyBatis 实现（CR-137） */
@Repository
@RequiredArgsConstructor
public class TeamMemberRepositoryImpl implements TeamMemberRepository {

    private final TeamMemberMapper teamMemberMapper;

    @Override
    public void save(TeamMember member) {
        teamMemberMapper.insert(toRow(member));
    }

    @Override
    public boolean tryAddApplication(long teamId, long userId, Instant appliedAt) {
        TeamMemberDO row = new TeamMemberDO();
        row.setTeamId(teamId);
        row.setUserId(userId);
        row.setRole(TeamMemberRole.MEMBER.name());
        row.setStatus(TeamMemberStatus.PENDING.name());
        row.setAppliedAt(appliedAt);
        try {
            teamMemberMapper.insert(row);
            return true;
        } catch (DuplicateKeyException e) {
            // 联合主键是"同一人对同一组队只有一条关系"的唯一防线：并发重复申请在此翻成 false
            return false;
        }
    }

    @Override
    public Optional<TeamMember> findByTeamAndUser(long teamId, long userId) {
        return Optional.ofNullable(teamMemberMapper.selectOne(teamId, userId))
                .map(TeamMemberRepositoryImpl::toDomain);
    }

    @Override
    public long countAccepted(long teamId) {
        return teamMemberMapper.countAccepted(teamId);
    }

    @Override
    public List<TeamMemberProfile> acceptedProfiles(long teamId) {
        return teamMemberMapper.acceptedProfiles(teamId).stream()
                .map(row -> new TeamMemberProfile(row.getUserId(), row.getNickname(), row.getAvatarUrl(),
                        TeamMemberRole.valueOf(row.getRole())))
                .toList();
    }

    @Override
    public List<TeamApplicationItem> pendingApplications(long teamId, int offset, int limit) {
        return teamMemberMapper.pendingApplications(teamId, offset, limit).stream()
                .map(row -> new TeamApplicationItem(row.getUserId(), row.getNickname(), row.getAvatarUrl(),
                        TeamMemberStatus.valueOf(row.getStatus()), row.getAppliedAt()))
                .toList();
    }

    @Override
    public long countPending(long teamId) {
        return teamMemberMapper.countPending(teamId);
    }

    @Override
    public int updateStatus(long teamId, long userId, TeamMemberStatus status, Instant decidedAt) {
        return teamMemberMapper.updateStatus(teamId, userId, status.name(), decidedAt);
    }

    private static TeamMemberDO toRow(TeamMember member) {
        TeamMemberDO row = new TeamMemberDO();
        row.setTeamId(member.getTeamId());
        row.setUserId(member.getUserId());
        row.setRole(member.getRole().name());
        row.setStatus(member.getStatus().name());
        row.setAppliedAt(member.getAppliedAt());
        row.setDecidedAt(member.getDecidedAt());
        return row;
    }

    private static TeamMember toDomain(TeamMemberDO row) {
        return TeamMember.rehydrate(row.getTeamId(), row.getUserId(),
                TeamMemberRole.valueOf(row.getRole()), TeamMemberStatus.valueOf(row.getStatus()),
                row.getAppliedAt(), row.getDecidedAt());
    }
}
