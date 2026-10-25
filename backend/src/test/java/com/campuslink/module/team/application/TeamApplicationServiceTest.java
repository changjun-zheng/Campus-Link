package com.campuslink.module.team.application;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.markdown.MarkdownRenderer;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.account.application.AccountApplicationService;
import com.campuslink.module.forum.application.ModerationApplicationService;
import com.campuslink.module.team.application.cmd.CreateTeamCommand;
import com.campuslink.module.team.application.cmd.TeamResults.ApplicationPage;
import com.campuslink.module.team.application.cmd.TeamResults.MyTeamPage;
import com.campuslink.module.team.application.cmd.TeamResults.TeamApplyResult;
import com.campuslink.module.team.application.cmd.TeamResults.TeamDetail;
import com.campuslink.module.team.application.cmd.TeamResults.TeamPage;
import com.campuslink.module.team.application.cmd.TeamResults.TeamStatusResult;
import com.campuslink.module.team.domain.gateway.TeamMemberRepository;
import com.campuslink.module.team.domain.gateway.TeamRepository;
import com.campuslink.module.team.domain.model.MyTeamItem;
import com.campuslink.module.team.domain.model.Team;
import com.campuslink.module.team.domain.model.TeamApplicationItem;
import com.campuslink.module.team.domain.model.TeamDirection;
import com.campuslink.module.team.domain.model.TeamListItem;
import com.campuslink.module.team.domain.model.TeamMember;
import com.campuslink.module.team.domain.model.TeamMemberProfile;
import com.campuslink.module.team.domain.model.TeamMemberRole;
import com.campuslink.module.team.domain.model.TeamMemberStatus;
import com.campuslink.module.team.domain.model.TeamStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 组队用例规则（CR-137）：权限、状态机、名额、联系方式可见性与分页归一 */
@ExtendWith(MockitoExtension.class)
class TeamApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");
    private static final long TEAM_ID = 10L;
    private static final long OWNER_ID = 1L;

    @Mock
    private TeamRepository teamRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private AccountApplicationService accountApplicationService;
    @Mock
    private ModerationApplicationService moderationApplicationService;
    @Mock
    private MarkdownRenderer markdownRenderer;
    @InjectMocks
    private TeamApplicationService service;

    @Test
    @DisplayName("发布招募：先查账号状态 / 机审，再落组队与 OWNER 关系，返回详情（发起人可见联系方式）")
    void createWritesTeamAndOwnerRelation() {
        Team saved = team(TeamStatus.RECRUITING, 3);
        when(teamRepository.save(any(Team.class))).thenReturn(saved);
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(saved));
        when(teamMemberRepository.acceptedProfiles(TEAM_ID)).thenReturn(List.of(
                new TeamMemberProfile(OWNER_ID, "发起人", null, TeamMemberRole.OWNER)));
        when(teamMemberRepository.findByTeamAndUser(TEAM_ID, OWNER_ID))
                .thenReturn(Optional.of(TeamMember.owner(TEAM_ID, OWNER_ID, NOW)));
        when(markdownRenderer.render("找队友")).thenReturn("<p>找队友</p>");

        TeamDetail detail = service.create(OWNER_ID, command("alGorithm", 3, null));

        verify(accountApplicationService).assertActiveForContent(OWNER_ID);
        verify(moderationApplicationService).ensureAllowed("蓝桥杯组队", "找队友", null);
        verify(teamMemberRepository).save(any(TeamMember.class));
        assertThat(detail.id()).isEqualTo(TEAM_ID);
        assertThat(detail.direction()).isEqualTo(TeamDirection.ALGORITHM);
        assertThat(detail.contact()).isEqualTo("qq:123456");
        assertThat(detail.contactVisible()).isTrue();
    }

    @Test
    @DisplayName("发布招募：方向非法 / 人数为空 / 截止日已过 → 1001，且不落库")
    void createRejectsInvalidInput() {
        assertThatThrownBy(() -> service.create(OWNER_ID, command("NOT_A_DIRECTION", 3, null)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));
        assertThatThrownBy(() -> service.create(OWNER_ID, command("ALGORITHM", null, null)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));
        assertThatThrownBy(() -> service.create(OWNER_ID, command("ALGORITHM", 3,
                java.time.LocalDate.now().minusDays(1))))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));

        verify(teamRepository, never()).save(any(Team.class));
    }

    @Test
    @DisplayName("列表：status 缺省 RECRUITING、direction 缺省不过滤；非法枚举 → 1001")
    void listDefaultsAndValidatesFilters() {
        when(teamRepository.count(null, TeamStatus.RECRUITING)).thenReturn(1L);
        when(teamRepository.page(null, TeamStatus.RECRUITING, 0, 100)).thenReturn(List.of(listItem()));

        TeamPage page = service.list(null, null, 0, 999);

        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(100);
        assertThat(page.total()).isEqualTo(1L);
        assertThat(page.list()).hasSize(1);

        assertThatThrownBy(() -> service.list("NOPE", null, 1, 20))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));
        assertThatThrownBy(() -> service.list(null, "NOPE", 1, 20))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));
    }

    @Test
    @DisplayName("详情：匿名 / 待审批者看不到联系方式；已加入成员可见")
    void detailHidesContactUnlessAccepted() {
        Team team = team(TeamStatus.RECRUITING, 3);
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team));
        when(teamMemberRepository.acceptedProfiles(TEAM_ID)).thenReturn(List.of(
                new TeamMemberProfile(OWNER_ID, "发起人", null, TeamMemberRole.OWNER)));
        when(markdownRenderer.render(any())).thenReturn("<p>简介</p>");

        TeamDetail anonymous = service.detail(null, TEAM_ID);
        assertThat(anonymous.contact()).isNull();
        assertThat(anonymous.contactVisible()).isFalse();
        assertThat(anonymous.myRole()).isNull();

        when(teamMemberRepository.findByTeamAndUser(TEAM_ID, 2L)).thenReturn(Optional.of(
                TeamMember.rehydrate(TEAM_ID, 2L, TeamMemberRole.MEMBER, TeamMemberStatus.PENDING, NOW, null)));
        TeamDetail pending = service.detail(2L, TEAM_ID);
        assertThat(pending.myApplicationStatus()).isEqualTo(TeamMemberStatus.PENDING);
        assertThat(pending.contact()).isNull();

        when(teamMemberRepository.findByTeamAndUser(TEAM_ID, 3L)).thenReturn(Optional.of(
                TeamMember.rehydrate(TEAM_ID, 3L, TeamMemberRole.MEMBER, TeamMemberStatus.ACCEPTED, NOW, NOW)));
        TeamDetail member = service.detail(3L, TEAM_ID);
        assertThat(member.myRole()).isEqualTo(TeamMemberRole.MEMBER);
        assertThat(member.contactVisible()).isTrue();
        assertThat(member.contact()).isEqualTo("qq:123456");
    }

    @Test
    @DisplayName("详情：组队不存在 → 6001")
    void detailMissingTeam() {
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(null, TEAM_ID))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.TEAM_NOT_FOUND));
    }

    @Test
    @DisplayName("申请：自己 → 6005；已关闭 → 6002；已满员 → 6003；已有关系 → 6004")
    void applyRejectsInvalidStates() {
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team(OWNER_ID, TeamStatus.RECRUITING, 3, 1)));
        assertThatThrownBy(() -> service.apply(OWNER_ID, TEAM_ID))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.CANNOT_APPLY_OWN_TEAM));

        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team(TeamStatus.CLOSED, 3)));
        assertThatThrownBy(() -> service.apply(2L, TEAM_ID))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.TEAM_NOT_RECRUITING));

        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team(TeamStatus.FULL, 3)));
        assertThatThrownBy(() -> service.apply(2L, TEAM_ID))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.TEAM_FULL));

        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team(TeamStatus.RECRUITING, 3)));
        when(teamMemberRepository.findByTeamAndUser(TEAM_ID, 2L)).thenReturn(Optional.of(
                TeamMember.rehydrate(TEAM_ID, 2L, TeamMemberRole.MEMBER, TeamMemberStatus.REJECTED, NOW, NOW)));
        assertThatThrownBy(() -> service.apply(2L, TEAM_ID))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.TEAM_APPLICATION_EXISTS));
    }

    @Test
    @DisplayName("申请：人数已满（状态未及时迁移）→ 6003；并发重复申请 repo 返回 false → 6004；成功返回 PENDING")
    void applyHandlesCapacityAndDuplicate() {
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team(TeamStatus.RECRUITING, 2)));
        when(teamMemberRepository.countAccepted(TEAM_ID)).thenReturn(2L);
        assertThatThrownBy(() -> service.apply(2L, TEAM_ID))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.TEAM_FULL));

        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team(TeamStatus.RECRUITING, 3)));
        when(teamMemberRepository.countAccepted(TEAM_ID)).thenReturn(1L);
        when(teamMemberRepository.tryAddApplication(eq(TEAM_ID), eq(2L), any(Instant.class))).thenReturn(false);
        assertThatThrownBy(() -> service.apply(2L, TEAM_ID))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.TEAM_APPLICATION_EXISTS));

        when(teamMemberRepository.tryAddApplication(eq(TEAM_ID), eq(2L), any(Instant.class))).thenReturn(true);
        TeamApplyResult result = service.apply(2L, TEAM_ID);
        assertThat(result.status()).isEqualTo(TeamMemberStatus.PENDING);
        assertThat(result.userId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("申请列表：非发起人 → 403；发起人拿到待审批分页")
    void listApplicationsRequiresOwner() {
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team(TeamStatus.RECRUITING, 3)));
        assertThatThrownBy(() -> service.listApplications(2L, TEAM_ID, 1, 20))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.FORBIDDEN));

        when(teamMemberRepository.countPending(TEAM_ID)).thenReturn(1L);
        when(teamMemberRepository.pendingApplications(TEAM_ID, 0, 20)).thenReturn(List.of(
                new TeamApplicationItem(2L, "申请人", null, TeamMemberStatus.PENDING, NOW)));
        ApplicationPage page = service.listApplications(OWNER_ID, TEAM_ID, 1, 20);
        assertThat(page.total()).isEqualTo(1L);
        assertThat(page.list().get(0).userId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("审批接受：PENDING → ACCEPTED；达到上限自动 FULL")
    void decideAcceptMarksTeamFull() {
        Team team = team(TeamStatus.RECRUITING, 2);
        when(teamRepository.findByIdForUpdate(TEAM_ID)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeamAndUser(TEAM_ID, 2L)).thenReturn(Optional.of(
                TeamMember.rehydrate(TEAM_ID, 2L, TeamMemberRole.MEMBER, TeamMemberStatus.PENDING, NOW, null)));
        when(teamMemberRepository.countAccepted(TEAM_ID)).thenReturn(1L);
        when(teamMemberRepository.updateStatus(eq(TEAM_ID), eq(2L), eq(TeamMemberStatus.ACCEPTED), any(Instant.class)))
                .thenReturn(1);

        TeamStatusResult result = service.decide(OWNER_ID, TEAM_ID, 2L, "accept");

        assertThat(result.status()).isEqualTo(TeamStatus.FULL);
        verify(teamRepository).updateStatus(TEAM_ID, TeamStatus.FULL);
    }

    @Test
    @DisplayName("审批接受：已关闭 → 6002；已满员 → 6003；非 PENDING（换个决定）→ 6006")
    void decideAcceptRejectsNonRecruiting() {
        when(teamRepository.findByIdForUpdate(TEAM_ID)).thenReturn(Optional.of(team(TeamStatus.CLOSED, 3)));
        when(teamMemberRepository.findByTeamAndUser(TEAM_ID, 2L)).thenReturn(Optional.of(
                TeamMember.rehydrate(TEAM_ID, 2L, TeamMemberRole.MEMBER, TeamMemberStatus.PENDING, NOW, null)));
        assertThatThrownBy(() -> service.decide(OWNER_ID, TEAM_ID, 2L, "ACCEPT"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.TEAM_NOT_RECRUITING));

        when(teamRepository.findByIdForUpdate(TEAM_ID)).thenReturn(Optional.of(team(TeamStatus.FULL, 3)));
        assertThatThrownBy(() -> service.decide(OWNER_ID, TEAM_ID, 2L, "ACCEPT"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.TEAM_FULL));

        when(teamRepository.findByIdForUpdate(TEAM_ID)).thenReturn(Optional.of(team(TeamStatus.RECRUITING, 3)));
        when(teamMemberRepository.findByTeamAndUser(TEAM_ID, 2L)).thenReturn(Optional.of(
                TeamMember.rehydrate(TEAM_ID, 2L, TeamMemberRole.MEMBER, TeamMemberStatus.REJECTED, NOW, NOW)));
        assertThatThrownBy(() -> service.decide(OWNER_ID, TEAM_ID, 2L, "ACCEPT"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.TEAM_APPLICATION_NOT_FOUND));
    }

    @Test
    @DisplayName("审批拒绝：写 REJECTED，组队状态不变；同一决定重复提交幂等")
    void decideRejectAndIdempotency() {
        Team team = team(TeamStatus.RECRUITING, 3);
        when(teamRepository.findByIdForUpdate(TEAM_ID)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeamAndUser(TEAM_ID, 2L)).thenReturn(Optional.of(
                TeamMember.rehydrate(TEAM_ID, 2L, TeamMemberRole.MEMBER, TeamMemberStatus.PENDING, NOW, null)));
        when(teamMemberRepository.updateStatus(eq(TEAM_ID), eq(2L), eq(TeamMemberStatus.REJECTED), any(Instant.class)))
                .thenReturn(1);

        service.decide(OWNER_ID, TEAM_ID, 2L, "REJECT");

        verify(teamRepository, never()).updateStatus(anyLong(), any());
        assertThat(team.getStatus()).isEqualTo(TeamStatus.RECRUITING);

        when(teamMemberRepository.findByTeamAndUser(TEAM_ID, 2L)).thenReturn(Optional.of(
                TeamMember.rehydrate(TEAM_ID, 2L, TeamMemberRole.MEMBER, TeamMemberStatus.ACCEPTED, NOW, NOW)));
        TeamStatusResult result = service.decide(OWNER_ID, TEAM_ID, 2L, "ACCEPT");
        assertThat(result.status()).isEqualTo(TeamStatus.RECRUITING);
        verify(teamMemberRepository, never()).updateStatus(
                eq(TEAM_ID), eq(2L), eq(TeamMemberStatus.ACCEPTED), any(Instant.class));
    }

    @Test
    @DisplayName("关闭：非发起人 403；发起人幂等关闭，已关闭不重复写库")
    void closeRequiresOwnerAndIsIdempotent() {
        Team team = team(TeamStatus.RECRUITING, 3);
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team));
        assertThatThrownBy(() -> service.close(2L, TEAM_ID))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.FORBIDDEN));

        TeamStatusResult result = service.close(OWNER_ID, TEAM_ID);
        assertThat(result.status()).isEqualTo(TeamStatus.CLOSED);
        verify(teamRepository).updateStatus(TEAM_ID, TeamStatus.CLOSED);

        result = service.close(OWNER_ID, TEAM_ID);
        assertThat(result.status()).isEqualTo(TeamStatus.CLOSED);
        verify(teamRepository).updateStatus(TEAM_ID, TeamStatus.CLOSED);
    }

    @Test
    @DisplayName("我的组队：role 缺省 owner，role=member 走成员关系，非法 role → 1001")
    void mineRoutesByRole() {
        when(teamRepository.countOfOwner(1L)).thenReturn(1L);
        when(teamRepository.pageOfOwner(1L, 0, 20)).thenReturn(List.of(myTeam()));
        MyTeamPage ownerPage = service.mine(1L, null, 1, 20);
        assertThat(ownerPage.total()).isEqualTo(1L);

        when(teamRepository.countOfMember(1L)).thenReturn(2L);
        when(teamRepository.pageOfMember(1L, 0, 20)).thenReturn(List.of());
        MyTeamPage memberPage = service.mine(1L, " Member ", 1, 20);
        assertThat(memberPage.total()).isEqualTo(2L);

        assertThatThrownBy(() -> service.mine(1L, "admin", 1, 20))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));
    }

    private static CreateTeamCommand command(String direction, Integer capacity, java.time.LocalDate deadline) {
        return new CreateTeamCommand("蓝桥杯组队", "找队友", direction, null, capacity, "qq:123456", deadline);
    }

    private static Team team(TeamStatus status, int capacity) {
        return team(OWNER_ID, status, capacity, 1);
    }

    private static Team team(long ownerId, TeamStatus status, int capacity, int ignoredAccepted) {
        return Team.rehydrate(TEAM_ID, ownerId, "蓝桥杯组队", "找队友", TeamDirection.ALGORITHM,
                null, capacity, "qq:123456", status, null, NOW);
    }

    private static TeamListItem listItem() {
        return new TeamListItem(TEAM_ID, "蓝桥杯组队", TeamDirection.ALGORITHM, null, 3, 1,
                TeamStatus.RECRUITING, null, OWNER_ID, "发起人", NOW);
    }

    private static MyTeamItem myTeam() {
        return new MyTeamItem(TEAM_ID, "蓝桥杯组队", TeamDirection.ALGORITHM, null, 3, 1,
                TeamStatus.RECRUITING, null, TeamMemberRole.OWNER, TeamMemberStatus.ACCEPTED, 0, NOW);
    }
}
