package com.campuslink.module.team.web;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.team.application.TeamApplicationService;
import com.campuslink.module.team.application.cmd.CreateTeamCommand;
import com.campuslink.module.team.application.cmd.DecideApplicationCommand;
import com.campuslink.module.team.application.cmd.TeamResults.ApplicationPage;
import com.campuslink.module.team.application.cmd.TeamResults.MyTeamPage;
import com.campuslink.module.team.application.cmd.TeamResults.TeamApplyResult;
import com.campuslink.module.team.application.cmd.TeamResults.TeamDetail;
import com.campuslink.module.team.application.cmd.TeamResults.TeamPage;
import com.campuslink.module.team.application.cmd.TeamResults.TeamStatusResult;
import com.campuslink.module.team.application.cmd.UpdateTeamStatusCommand;
import com.campuslink.module.team.domain.model.MyTeamItem;
import com.campuslink.module.team.domain.model.TeamApplicationItem;
import com.campuslink.module.team.domain.model.TeamDirection;
import com.campuslink.module.team.domain.model.TeamListItem;
import com.campuslink.module.team.domain.model.TeamMemberProfile;
import com.campuslink.module.team.domain.model.TeamMemberRole;
import com.campuslink.module.team.domain.model.TeamMemberStatus;
import com.campuslink.module.team.domain.model.TeamStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 组队端点（CR-137）的授权三态：公开端点匿名可读且带上可选身份；
 * 6 个受保护端点匿名一律 4001 且不触达用例；登录态 userId 只从令牌取。
 */
class TeamControllerAuthTest {

    private static final long TEAM_ID = 10L;
    private static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");

    private final TeamApplicationService service = mock(TeamApplicationService.class);
    private final TeamController controller = new TeamController(service);

    @Test
    @DisplayName("公开列表：匿名可读，不经过 CurrentUser")
    void publicListWorksAnonymous() {
        when(service.list(null, null, 1, 20)).thenReturn(new TeamPage(List.of(listItem()), 1L, 1, 20));

        controller.listTeams(null, null, 1, 20);

        verify(service).list(null, null, 1, 20);
    }

    @Test
    @DisplayName("公开详情：匿名传 null；登录态把令牌 id 作为 viewer")
    void publicDetailPassesOptionalViewer() {
        when(service.detail(null, TEAM_ID)).thenReturn(detail());
        controller.teamDetail(TEAM_ID, null);

        Authentication auth = new UsernamePasswordAuthenticationToken(9L, "", List.of());
        when(service.detail(9L, TEAM_ID)).thenReturn(detail());
        controller.teamDetail(TEAM_ID, auth);

        verify(service).detail(null, TEAM_ID);
        verify(service).detail(9L, TEAM_ID);
    }

    @Test
    @DisplayName("匿名发布 → 4001，且不触达用例")
    void anonymousCreateIsUnauthorized() {
        assertThatThrownBy(() -> controller.createTeam(command(), null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));
        verify(service, never()).create(anyLong(), any());
    }

    @Test
    @DisplayName("匿名申请 / 查申请列表 / 审批 / 关闭 / 我的组队 → 4001")
    void anonymousProtectedEndpointsAreUnauthorized() {
        assertThatThrownBy(() -> controller.applyToTeam(TEAM_ID, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));
        assertThatThrownBy(() -> controller.listTeamApplications(TEAM_ID, null, 1, 20))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));
        assertThatThrownBy(() -> controller.decideApplication(TEAM_ID, 2L,
                new DecideApplicationCommand("ACCEPT"), null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));
        assertThatThrownBy(() -> controller.updateTeamStatus(TEAM_ID, new UpdateTeamStatusCommand("CLOSED"), null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));
        assertThatThrownBy(() -> controller.myTeams(null, null, 1, 20))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));

        verify(service, never()).apply(anyLong(), anyLong());
        verify(service, never()).listApplications(anyLong(), anyLong(), anyInt(), anyInt());
        verify(service, never()).decide(anyLong(), anyLong(), anyLong(), any());
        verify(service, never()).close(anyLong(), anyLong());
        verify(service, never()).mine(anyLong(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("登录态：userId 只从令牌取；关闭入参只接受 CLOSED")
    void loggedInPassesPrincipalAndValidatesStatus() {
        Authentication auth = new UsernamePasswordAuthenticationToken(1L, "", List.of());
        when(service.create(1L, command())).thenReturn(detail());
        when(service.apply(1L, TEAM_ID)).thenReturn(new TeamApplyResult(1L, TeamMemberStatus.PENDING, NOW));
        when(service.listApplications(1L, TEAM_ID, 1, 20)).thenReturn(new ApplicationPage(List.of(), 0L, 1, 20));
        when(service.decide(1L, TEAM_ID, 2L, "ACCEPT")).thenReturn(new TeamStatusResult(TeamStatus.RECRUITING));
        when(service.close(1L, TEAM_ID)).thenReturn(new TeamStatusResult(TeamStatus.CLOSED));
        when(service.mine(1L, "member", 1, 20)).thenReturn(new MyTeamPage(List.of(), 0L, 1, 20));

        controller.createTeam(command(), auth);
        controller.applyToTeam(TEAM_ID, auth);
        controller.listTeamApplications(TEAM_ID, auth, 1, 20);
        controller.decideApplication(TEAM_ID, 2L, new DecideApplicationCommand("ACCEPT"), auth);
        controller.updateTeamStatus(TEAM_ID, new UpdateTeamStatusCommand("closed"), auth);
        controller.myTeams(auth, "member", 1, 20);

        verify(service).create(1L, command());
        verify(service).apply(1L, TEAM_ID);
        verify(service).listApplications(1L, TEAM_ID, 1, 20);
        verify(service).decide(1L, TEAM_ID, 2L, "ACCEPT");
        verify(service).close(1L, TEAM_ID);
        verify(service).mine(1L, "member", 1, 20);

        assertThatThrownBy(() -> controller.updateTeamStatus(TEAM_ID,
                new UpdateTeamStatusCommand("RECRUITING"), auth))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));
    }

    private static CreateTeamCommand command() {
        return new CreateTeamCommand("蓝桥杯组队", "找队友", "ALGORITHM", null, 3, "qq:123456", null);
    }

    private static TeamDetail detail() {
        return new TeamDetail(TEAM_ID, "蓝桥杯组队", "<p>找队友</p>", TeamDirection.ALGORITHM, null, 3, 1,
                TeamStatus.RECRUITING, null, 1L, "发起人", NOW,
                List.of(new TeamMemberProfile(1L, "发起人", null, TeamMemberRole.OWNER)),
                null, null, null, false);
    }

    private static TeamListItem listItem() {
        return new TeamListItem(TEAM_ID, "蓝桥杯组队", TeamDirection.ALGORITHM, null, 3, 1,
                TeamStatus.RECRUITING, null, 1L, "发起人", NOW);
    }

    @SuppressWarnings("unused")
    private static MyTeamItem myTeam() {
        return new MyTeamItem(TEAM_ID, "蓝桥杯组队", TeamDirection.ALGORITHM, null, 3, 1,
                TeamStatus.RECRUITING, null, TeamMemberRole.OWNER, TeamMemberStatus.ACCEPTED, 0, NOW);
    }

    @SuppressWarnings("unused")
    private static TeamApplicationItem application() {
        return new TeamApplicationItem(2L, "申请人", null, TeamMemberStatus.PENDING, NOW);
    }
}
