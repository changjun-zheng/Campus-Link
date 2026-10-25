package com.campuslink.module.team.domain.gateway;

import com.campuslink.module.team.domain.model.MyTeamItem;
import com.campuslink.module.team.domain.model.Team;
import com.campuslink.module.team.domain.model.TeamDirection;
import com.campuslink.module.team.domain.model.TeamListItem;
import com.campuslink.module.team.domain.model.TeamStatus;

import java.util.List;
import java.util.Optional;

/**
 * 出站端口：组队招募（F-TEAM-001 / CR-137）。
 *
 * <p>列表 / 我的两条读路径返回领域只读投影（{@link TeamListItem} / {@link MyTeamItem}），
 * 由适配器用一条带 {@code users} 联表与计数子查询的 SQL 取齐；详情走 {@link #findById}，
 * 联系方式在适配器内解密。
 */
public interface TeamRepository {

    /** 新建招募（含 owner 成员行由调用方在同一事务内另写） */
    Team save(Team team);

    Optional<Team> findById(long id);

    /** 审批 / 关闭时锁住组队行（同事务内），避免并发接受把人数顶过上限 */
    Optional<Team> findByIdForUpdate(long id);

    /** 广场列表：{@code status} 非空才过滤；按创建时间倒序 */
    List<TeamListItem> page(TeamDirection direction, TeamStatus status, int offset, int limit);

    long count(TeamDirection direction, TeamStatus status);

    /** 我发起的组队：含待审批数，按创建时间倒序 */
    List<MyTeamItem> pageOfOwner(long ownerId, int offset, int limit);

    long countOfOwner(long ownerId);

    /** 我申请的组队：只含 MEMBER 关系（任何状态），按申请时间倒序 */
    List<MyTeamItem> pageOfMember(long userId, int offset, int limit);

    long countOfMember(long userId);

    /** 状态迁移（FULL / CLOSED）；调用方已做业务校验 */
    void updateStatus(long teamId, TeamStatus status);
}
