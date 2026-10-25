package com.campuslink.module.team.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 组队 SQL 的口径断言（CR-137）：读 {@code @Select} / {@code @Update} 注解文本
 * （Mockito 结构上捕不到 SQL 条件，与 CR-104 / CR-133 / CR-135 同法）。
 */
class TeamMapperSqlTest {

    @Test
    @DisplayName("列表 SQL：状态 / 方向动态过滤 + 已加入人数子查询 + 参数化分页，不拼字符串")
    void listSqlIsFilteredAndParameterized() throws NoSuchMethodException {
        String sql = selectSql(TeamMapper.class, "page", String.class, String.class, int.class, int.class);

        assertThat(sql)
                .contains("FROM teams t")
                .contains("JOIN users u ON u.id = t.owner_id")
                .contains("tm.status = 'ACCEPTED'")
                .contains("AS accepted_count")
                .contains("<if test=\"direction != null\"> AND t.direction = #{direction} </if>")
                .contains("<if test=\"status != null\"> AND t.status = #{status} </if>")
                .contains("ORDER BY t.created_at DESC, t.id DESC")
                .contains("LIMIT #{limit} OFFSET #{offset}")
                .doesNotContain("${");
    }

    @Test
    @DisplayName("列表计数：与分页同一 WHERE（total 同源）")
    void listCountSharesWhere() throws NoSuchMethodException {
        String sql = selectSql(TeamMapper.class, "count", String.class, String.class);

        assertThat(sql)
                .contains("FROM teams t")
                .contains("<if test=\"direction != null\"> AND t.direction = #{direction} </if>")
                .contains("<if test=\"status != null\"> AND t.status = #{status} </if>")
                .doesNotContain("${");
    }

    @Test
    @DisplayName("我的组队：owner 带待审批数、member 只取 MEMBER 关系且含已拒绝状态")
    void myTeamSqlRoutesByRole() throws NoSuchMethodException {
        String owner = selectSql(TeamMapper.class, "pageOfOwner", long.class, int.class, int.class);
        assertThat(owner)
                .contains("WHERE t.owner_id = #{ownerId}")
                .contains("'OWNER' AS my_role")
                .contains("tm2.status = 'PENDING'")
                .contains("AS pending_count")
                .doesNotContain("${");

        String member = selectSql(TeamMapper.class, "pageOfMember", long.class, int.class, int.class);
        assertThat(member)
                .contains("FROM team_members tm")
                .contains("tm.role = 'MEMBER'")
                .contains("tm.status AS my_status")
                .contains("ORDER BY tm.applied_at DESC, t.id DESC")
                .contains("LIMIT #{limit} OFFSET #{offset}")
                .doesNotContain("${");
    }

    @Test
    @DisplayName("成员 SQL：成员按角色排序；待审批只列 PENDING；审批条件带 status='PENDING'")
    void memberSqlIsScoped() throws NoSuchMethodException {
        String profiles = selectSql(TeamMemberMapper.class, "acceptedProfiles", long.class);
        assertThat(profiles)
                .contains("tm.status = 'ACCEPTED'")
                .contains("JOIN users u ON u.id = tm.user_id")
                .contains("CASE WHEN u.status = 'DEACTIVATED'")
                .doesNotContain("${");

        String pending = selectSql(TeamMemberMapper.class, "pendingApplications", long.class, int.class, int.class);
        assertThat(pending)
                .contains("tm.status = 'PENDING'")
                .contains("LIMIT #{limit} OFFSET #{offset}")
                .doesNotContain("${");

        String update = updateSql(TeamMemberMapper.class, "updateStatus", long.class, long.class, String.class,
                java.time.Instant.class);
        assertThat(update)
                .contains("SET status = #{status}")
                .contains("AND status = 'PENDING'")
                .doesNotContain("${");
    }

    private static String selectSql(Class<?> mapper, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        return String.join("", mapper.getMethod(name, parameterTypes).getAnnotation(Select.class).value());
    }

    private static String updateSql(Class<?> mapper, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        return String.join("", mapper.getMethod(name, parameterTypes).getAnnotation(Update.class).value());
    }
}
