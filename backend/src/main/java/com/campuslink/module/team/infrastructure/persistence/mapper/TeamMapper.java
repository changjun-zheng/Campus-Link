package com.campuslink.module.team.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campuslink.module.team.infrastructure.persistence.MyTeamDO;
import com.campuslink.module.team.infrastructure.persistence.TeamDO;
import com.campuslink.module.team.infrastructure.persistence.TeamListDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** teams 的 MyBatis-Plus Mapper（CR-137） */
@Mapper
public interface TeamMapper extends BaseMapper<TeamDO> {

    /**
     * 广场列表（CR-137）：组队字段 + 发起人昵称（已注销回落占位）+ 已加入人数，一条 SQL 取齐。
     * 只列 {@code capacity} 与状态，不拉简介 / 联系方式（联系方式永不出现在列表）。
     * 参数化 {@code LIMIT / OFFSET}，不拼字符串；排序带 id 作稳定第二键。
     */
    @Select("""
            <script>
            SELECT t.id, t.title, t.direction, t.target, t.capacity, t.status, t.deadline,
                   t.owner_id, t.created_at,
                   CASE WHEN u.status = 'DEACTIVATED' THEN '已注销用户' ELSE u.nickname END AS owner_nickname,
                   (SELECT COUNT(*) FROM team_members tm
                    WHERE tm.team_id = t.id AND tm.status = 'ACCEPTED') AS accepted_count
            FROM teams t
            JOIN users u ON u.id = t.owner_id
            WHERE 1 = 1
            <if test="direction != null"> AND t.direction = #{direction} </if>
            <if test="status != null"> AND t.status = #{status} </if>
            ORDER BY t.created_at DESC, t.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<TeamListDO> page(@Param("direction") String direction,
                          @Param("status") String status,
                          @Param("offset") int offset,
                          @Param("limit") int limit);

    /** 广场总数：与 {@link #page} 同一 WHERE（total 与页内容同源） */
    @Select("""
            <script>
            SELECT COUNT(*)
            FROM teams t
            WHERE 1 = 1
            <if test="direction != null"> AND t.direction = #{direction} </if>
            <if test="status != null"> AND t.status = #{status} </if>
            </script>
            """)
    long count(@Param("direction") String direction, @Param("status") String status);

    /** 我发起的组队：含待审批数（发起人在「我的组队」页据此提示） */
    @Select("""
            SELECT t.id, t.title, t.direction, t.target, t.capacity, t.status, t.deadline, t.created_at,
                   (SELECT COUNT(*) FROM team_members tm
                    WHERE tm.team_id = t.id AND tm.status = 'ACCEPTED') AS accepted_count,
                   'OWNER' AS my_role,
                   'ACCEPTED' AS my_status,
                   (SELECT COUNT(*) FROM team_members tm2
                    WHERE tm2.team_id = t.id AND tm2.status = 'PENDING') AS pending_count
            FROM teams t
            WHERE t.owner_id = #{ownerId}
            ORDER BY t.created_at DESC, t.id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<MyTeamDO> pageOfOwner(@Param("ownerId") long ownerId,
                               @Param("offset") int offset,
                               @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*)
            FROM teams t
            WHERE t.owner_id = #{ownerId}
            """)
    long countOfOwner(@Param("ownerId") long ownerId);

    /**
     * 我申请的组队：只取 MEMBER 关系（任何状态），按申请时间倒序。
     * 申请已拒绝的仍列出 —— 用户有权知道"申请过、被拒了"，藏掉会让"我的组队"对不上历史。
     */
    @Select("""
            SELECT t.id, t.title, t.direction, t.target, t.capacity, t.status, t.deadline, t.created_at,
                   (SELECT COUNT(*) FROM team_members tm2
                    WHERE tm2.team_id = t.id AND tm2.status = 'ACCEPTED') AS accepted_count,
                   tm.role AS my_role,
                   tm.status AS my_status,
                   0 AS pending_count
            FROM team_members tm
            JOIN teams t ON t.id = tm.team_id
            WHERE tm.user_id = #{userId} AND tm.role = 'MEMBER'
            ORDER BY tm.applied_at DESC, t.id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<MyTeamDO> pageOfMember(@Param("userId") long userId,
                                @Param("offset") int offset,
                                @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*)
            FROM team_members tm
            WHERE tm.user_id = #{userId} AND tm.role = 'MEMBER'
            """)
    long countOfMember(@Param("userId") long userId);
}
