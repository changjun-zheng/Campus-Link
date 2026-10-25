package com.campuslink.module.team.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campuslink.common.crypto.CryptoService;
import com.campuslink.module.team.domain.gateway.TeamRepository;
import com.campuslink.module.team.domain.model.MyTeamItem;
import com.campuslink.module.team.domain.model.Team;
import com.campuslink.module.team.domain.model.TeamDirection;
import com.campuslink.module.team.domain.model.TeamListItem;
import com.campuslink.module.team.domain.model.TeamMemberRole;
import com.campuslink.module.team.domain.model.TeamMemberStatus;
import com.campuslink.module.team.domain.model.TeamStatus;
import com.campuslink.module.team.infrastructure.persistence.mapper.TeamMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 适配器：TeamRepository 端口的 MyBatis-Plus 实现（CR-137）。
 *
 * <p>{@code contact_enc} 的加解密只发生在本类：写入前 AES-GCM 加密，读取时解密后交给领域对象；
 * 列表 / 我的两条 SQL 不 SELECT 该列，故列表路径不产生解密成本，也不存在"误下发"的路径。
 */
@Repository
@RequiredArgsConstructor
public class TeamRepositoryImpl implements TeamRepository {

    private final TeamMapper teamMapper;
    private final CryptoService cryptoService;

    @Override
    public Team save(Team team) {
        TeamDO row = new TeamDO();
        row.setOwnerId(team.getOwnerId());
        row.setTitle(team.getTitle());
        row.setDescriptionMd(team.getDescriptionMd());
        row.setDirection(team.getDirection().name());
        row.setTarget(team.getTarget());
        row.setCapacity(team.getCapacity());
        row.setContactEnc(cryptoService.encrypt(team.getContact()));
        row.setStatus(team.getStatus().name());
        row.setDeadline(team.getDeadline());
        teamMapper.insert(row);
        return Team.rehydrate(row.getId(), team.getOwnerId(), team.getTitle(), team.getDescriptionMd(),
                team.getDirection(), team.getTarget(), team.getCapacity(), team.getContact(),
                TeamStatus.valueOf(row.getStatus()), team.getDeadline(), team.getCreatedAt());
    }

    @Override
    public Optional<Team> findById(long id) {
        return Optional.ofNullable(teamMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<Team> findByIdForUpdate(long id) {
        TeamDO row = teamMapper.selectOne(new LambdaQueryWrapper<TeamDO>()
                .eq(TeamDO::getId, id)
                .last("FOR UPDATE"));
        return Optional.ofNullable(row).map(this::toDomain);
    }

    @Override
    public List<TeamListItem> page(TeamDirection direction, TeamStatus status, int offset, int limit) {
        return teamMapper.page(direction == null ? null : direction.name(),
                        status == null ? null : status.name(), offset, limit).stream()
                .map(TeamRepositoryImpl::toListItem)
                .toList();
    }

    @Override
    public long count(TeamDirection direction, TeamStatus status) {
        return teamMapper.count(direction == null ? null : direction.name(),
                status == null ? null : status.name());
    }

    @Override
    public List<MyTeamItem> pageOfOwner(long ownerId, int offset, int limit) {
        return teamMapper.pageOfOwner(ownerId, offset, limit).stream()
                .map(TeamRepositoryImpl::toMyTeam)
                .toList();
    }

    @Override
    public long countOfOwner(long ownerId) {
        return teamMapper.countOfOwner(ownerId);
    }

    @Override
    public List<MyTeamItem> pageOfMember(long userId, int offset, int limit) {
        return teamMapper.pageOfMember(userId, offset, limit).stream()
                .map(TeamRepositoryImpl::toMyTeam)
                .toList();
    }

    @Override
    public long countOfMember(long userId) {
        return teamMapper.countOfMember(userId);
    }

    @Override
    public void updateStatus(long teamId, TeamStatus status) {
        teamMapper.update(null, new LambdaUpdateWrapper<TeamDO>()
                .set(TeamDO::getStatus, status.name())
                .eq(TeamDO::getId, teamId));
    }

    private Team toDomain(TeamDO row) {
        return Team.rehydrate(row.getId(), row.getOwnerId(), row.getTitle(), row.getDescriptionMd(),
                TeamDirection.valueOf(row.getDirection()), row.getTarget(), row.getCapacity(),
                cryptoService.decrypt(row.getContactEnc()), TeamStatus.valueOf(row.getStatus()),
                row.getDeadline(), row.getCreatedAt());
    }

    private static TeamListItem toListItem(TeamListDO row) {
        return new TeamListItem(row.getId(), row.getTitle(), TeamDirection.valueOf(row.getDirection()),
                row.getTarget(), row.getCapacity(), row.getAcceptedCount() == null ? 0L : row.getAcceptedCount(),
                TeamStatus.valueOf(row.getStatus()), row.getDeadline(), row.getOwnerId(),
                row.getOwnerNickname(), row.getCreatedAt());
    }

    private static MyTeamItem toMyTeam(MyTeamDO row) {
        return new MyTeamItem(row.getId(), row.getTitle(), TeamDirection.valueOf(row.getDirection()),
                row.getTarget(), row.getCapacity(), row.getAcceptedCount() == null ? 0L : row.getAcceptedCount(),
                TeamStatus.valueOf(row.getStatus()), row.getDeadline(),
                TeamMemberRole.valueOf(row.getMyRole()), TeamMemberStatus.valueOf(row.getMyStatus()),
                row.getPendingCount() == null ? 0L : row.getPendingCount(), row.getCreatedAt());
    }
}
