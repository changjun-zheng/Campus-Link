package com.campuslink.module.team.infrastructure.persistence.mapper;

import com.campuslink.module.team.infrastructure.persistence.TeamApplicationDO;
import com.campuslink.module.team.infrastructure.persistence.TeamMemberDO;
import com.campuslink.module.team.infrastructure.persistence.TeamMemberProfileDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

/**
 * team_members 的 Mapper（CR-137）：联合主键 {@code (team_id, user_id)} 不适合 MyBatis-Plus 的
 * 单 id 模型，故不继承 {@code BaseMapper}，全部手写参数化 SQL。
 */
@Mapper
public interface TeamMemberMapper {

    @Insert("""
            INSERT INTO team_members (team_id, user_id, role, status, applied_at, decided_at)
            VALUES (#{teamId}, #{userId}, #{role}, #{status}, #{appliedAt}, #{decidedAt})
            """)
    int insert(TeamMemberDO row);

    @Select("""
            SELECT team_id, user_id, role, status, applied_at, decided_at
            FROM team_members
            WHERE team_id = #{teamId} AND user_id = #{userId}
            """)
    TeamMemberDO selectOne(@Param("teamId") long teamId, @Param("userId") long userId);

    @Select("""
            SELECT COUNT(*)
            FROM team_members
            WHERE team_id = #{teamId} AND status = 'ACCEPTED'
            """)
    long countAccepted(@Param("teamId") long teamId);

    /** 详情页成员：发起人排第一，其余按加队时间升序；已注销昵称回落占位 */
    @Select("""
            SELECT tm.user_id,
                   CASE WHEN u.status = 'DEACTIVATED' THEN '已注销用户' ELSE u.nickname END AS nickname,
                   u.avatar_url,
                   tm.role
            FROM team_members tm
            JOIN users u ON u.id = tm.user_id
            WHERE tm.team_id = #{teamId} AND tm.status = 'ACCEPTED'
            ORDER BY CASE WHEN tm.role = 'OWNER' THEN 0 ELSE 1 END, tm.decided_at ASC, tm.user_id ASC
            """)
    List<TeamMemberProfileDO> acceptedProfiles(@Param("teamId") long teamId);

    @Select("""
            SELECT tm.user_id,
                   CASE WHEN u.status = 'DEACTIVATED' THEN '已注销用户' ELSE u.nickname END AS nickname,
                   u.avatar_url,
                   tm.status,
                   tm.applied_at
            FROM team_members tm
            JOIN users u ON u.id = tm.user_id
            WHERE tm.team_id = #{teamId} AND tm.status = 'PENDING'
            ORDER BY tm.applied_at ASC, tm.user_id ASC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<TeamApplicationDO> pendingApplications(@Param("teamId") long teamId,
                                                @Param("offset") int offset,
                                                @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*)
            FROM team_members
            WHERE team_id = #{teamId} AND status = 'PENDING'
            """)
    long countPending(@Param("teamId") long teamId);

    /** 条件带 {@code status='PENDING'}：并发审批只有一个事务能改到行 */
    @Update("""
            UPDATE team_members
            SET status = #{status}, decided_at = #{decidedAt}
            WHERE team_id = #{teamId} AND user_id = #{userId} AND status = 'PENDING'
            """)
    int updateStatus(@Param("teamId") long teamId,
                     @Param("userId") long userId,
                     @Param("status") String status,
                     @Param("decidedAt") Instant decidedAt);
}
