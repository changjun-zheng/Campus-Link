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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 组队广场用例（F-TEAM-001 / CR-137）：发布 / 列表 / 详情 / 申请 / 审批 / 关闭 / 我的组队。
 *
 * <p>业务口径（方案 §7）：
 * ① 申请 → 发起人审批（接受 / 拒绝）；接受后人数达上限自动 {@code FULL}；
 * ② 被拒后不可再次申请（联合主键 + 状态判定 → {@code 6004}）；不能申请自己发起的组队（{@code 6005}）；
 * ③ 已关闭 {@code 6002}、已满员 {@code 6003}，两者文案分开（用户下一步动作不同：等重开 vs 换一队）；
 * ④ 联系方式只在发起人 / 已加入成员视角下发，其余视角为 {@code null}；
 * ⑤ 分页归一：{@code page >= 1}、{@code 1 <= size <= 100}，与通知 / 私信同口径。
 *
 * <p>跨上下文只调用对方 {@code application}（ADR-012 / 守护测试 G4）：账号状态走 account，
 * 机审走 forum；本类不出现其他上下文的 domain 类型。
 */
@Service
@RequiredArgsConstructor
public class TeamApplicationService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final AccountApplicationService accountApplicationService;
    private final ModerationApplicationService moderationApplicationService;
    private final MarkdownRenderer markdownRenderer;

    /** 发布招募：发起人同时写一条 OWNER / ACCEPTED 关系，人数含发起人 */
    @Transactional
    public TeamDetail create(long ownerId, CreateTeamCommand command) {
        accountApplicationService.assertActiveForContent(ownerId);
        TeamDirection direction = TeamDirection.parse(command.direction())
                .orElseThrow(() -> new ApiException(ResultCode.INVALID_PARAM));
        if (command.capacity() == null) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
        if (command.deadline() != null && command.deadline().isBefore(LocalDate.now())) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
        // 标题 / 简介 / 目标赛事是公开文本；联系方式只做格式校验，不进词表（它本就是联系方式）
        moderationApplicationService.ensureAllowed(command.title(), command.descriptionMd(), command.target());

        Instant now = Instant.now();
        Team team;
        try {
            team = Team.open(ownerId, command.title(), command.descriptionMd(), direction,
                    command.target(), command.capacity(), command.contact(), command.deadline(), now);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
        Team saved = teamRepository.save(team);
        teamMemberRepository.save(TeamMember.owner(saved.getId(), ownerId, now));
        return detail(ownerId, saved.getId());
    }

    /**
     * 广场列表：{@code status} 缺省只列招募中（广场默认语义），{@code direction} 缺省不过滤。
     * 匿名可读；联系方式不在列表出参中。
     */
    public TeamPage list(String directionRaw, String statusRaw, int page, int size) {
        int currentPage = normalizePage(page);
        int pageSize = normalizeSize(size);
        TeamDirection direction = null;
        if (directionRaw != null && !directionRaw.isBlank()) {
            direction = TeamDirection.parse(directionRaw)
                    .orElseThrow(() -> new ApiException(ResultCode.INVALID_PARAM));
        }
        TeamStatus status = TeamStatus.parse(statusRaw);
        if (status == null) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
        long total = teamRepository.count(direction, status);
        List<TeamListItem> items = teamRepository
                .page(direction, status, offsetOf(currentPage, pageSize), pageSize);
        return new TeamPage(items, total, currentPage, pageSize);
    }

    /** 详情（公开）：匿名可读；登录者额外得到"我的关系"，仅发起人 / 已加入成员得到联系方式 */
    public TeamDetail detail(Long viewerId, long teamId) {
        Team team = requireTeam(teamId);
        List<TeamMemberProfile> members = teamMemberRepository.acceptedProfiles(teamId);
        String ownerNickname = members.stream()
                .filter(m -> m.role() == TeamMemberRole.OWNER)
                .map(TeamMemberProfile::nickname)
                .findFirst()
                .orElse("已注销用户");

        TeamMemberRole myRole = null;
        TeamMemberStatus myStatus = null;
        boolean contactVisible = false;
        if (viewerId != null) {
            Optional<TeamMember> relation = teamMemberRepository.findByTeamAndUser(teamId, viewerId);
            if (relation.isPresent()) {
                myRole = relation.get().getRole();
                myStatus = relation.get().getStatus();
                contactVisible = relation.get().isAccepted();
            }
        }
        return new TeamDetail(team.getId(), team.getTitle(), markdownRenderer.render(team.getDescriptionMd()),
                team.getDirection(), team.getTarget(), team.getCapacity(), members.size(), team.getStatus(),
                team.getDeadline(), team.getOwnerId(), ownerNickname, team.getCreatedAt(), members,
                myRole, myStatus, contactVisible ? team.getContact() : null, contactVisible);
    }

    /** 申请加入：待审批；同一人对同一组队已有关系（含被拒）一律 6004 */
    @Transactional
    public TeamApplyResult apply(long userId, long teamId) {
        accountApplicationService.assertActiveForContent(userId);
        Team team = requireTeam(teamId);
        if (team.getOwnerId() == userId) {
            throw new ApiException(ResultCode.CANNOT_APPLY_OWN_TEAM);
        }
        if (team.getStatus() == TeamStatus.CLOSED) {
            throw new ApiException(ResultCode.TEAM_NOT_RECRUITING);
        }
        if (team.getStatus() == TeamStatus.FULL) {
            throw new ApiException(ResultCode.TEAM_FULL);
        }
        if (teamMemberRepository.findByTeamAndUser(teamId, userId).isPresent()) {
            throw new ApiException(ResultCode.TEAM_APPLICATION_EXISTS);
        }
        if (teamMemberRepository.countAccepted(teamId) >= team.getCapacity()) {
            throw new ApiException(ResultCode.TEAM_FULL);
        }
        Instant now = Instant.now();
        if (!teamMemberRepository.tryAddApplication(teamId, userId, now)) {
            throw new ApiException(ResultCode.TEAM_APPLICATION_EXISTS);
        }
        return new TeamApplyResult(userId, TeamMemberStatus.PENDING, now);
    }

    /** 发起人待审批列表（只列 PENDING，按申请时间升序） */
    public ApplicationPage listApplications(long ownerId, long teamId, int page, int size) {
        Team team = requireTeam(teamId);
        requireOwner(team, ownerId);
        int currentPage = normalizePage(page);
        int pageSize = normalizeSize(size);
        long total = teamMemberRepository.countPending(teamId);
        List<TeamApplicationItem> items = teamMemberRepository
                .pendingApplications(teamId, offsetOf(currentPage, pageSize), pageSize);
        return new ApplicationPage(items, total, currentPage, pageSize);
    }

    /**
     * 审批：锁组队行 → 校验发起人 → 只允许 PENDING；接受时再查名额，达到上限就置 {@code FULL}。
     * 同一决定重复提交幂等返回当前状态；换个决定则 {@code 6006}（已处理）。
     */
    @Transactional
    public TeamStatusResult decide(long ownerId, long teamId, long applicantId, String decisionRaw) {
        boolean accept = parseDecision(decisionRaw);
        Team team = teamRepository.findByIdForUpdate(teamId)
                .orElseThrow(() -> new ApiException(ResultCode.TEAM_NOT_FOUND));
        requireOwner(team, ownerId);
        TeamMember member = teamMemberRepository.findByTeamAndUser(teamId, applicantId)
                .orElseThrow(() -> new ApiException(ResultCode.TEAM_APPLICATION_NOT_FOUND));
        TeamMemberStatus target = accept ? TeamMemberStatus.ACCEPTED : TeamMemberStatus.REJECTED;
        if (member.getStatus() == target) {
            return new TeamStatusResult(team.getStatus());
        }
        if (!member.isPending()) {
            throw new ApiException(ResultCode.TEAM_APPLICATION_NOT_FOUND);
        }
        Instant now = Instant.now();
        if (accept) {
            if (!team.isRecruiting()) {
                throw new ApiException(team.getStatus() == TeamStatus.FULL
                        ? ResultCode.TEAM_FULL : ResultCode.TEAM_NOT_RECRUITING);
            }
            long accepted = teamMemberRepository.countAccepted(teamId);
            if (accepted >= team.getCapacity()) {
                throw new ApiException(ResultCode.TEAM_FULL);
            }
            if (teamMemberRepository.updateStatus(teamId, applicantId, TeamMemberStatus.ACCEPTED, now) == 0) {
                throw new ApiException(ResultCode.TEAM_APPLICATION_NOT_FOUND);
            }
            if (accepted + 1 >= team.getCapacity()) {
                team.markFull();
                teamRepository.updateStatus(teamId, TeamStatus.FULL);
            }
        } else if (teamMemberRepository.updateStatus(teamId, applicantId, TeamMemberStatus.REJECTED, now) == 0) {
            throw new ApiException(ResultCode.TEAM_APPLICATION_NOT_FOUND);
        }
        return new TeamStatusResult(team.getStatus());
    }

    /** 发起人关闭：幂等；关闭后不可重开（方案 D5） */
    @Transactional
    public TeamStatusResult close(long ownerId, long teamId) {
        Team team = requireTeam(teamId);
        requireOwner(team, ownerId);
        if (team.getStatus() != TeamStatus.CLOSED) {
            team.close();
            teamRepository.updateStatus(teamId, TeamStatus.CLOSED);
        }
        return new TeamStatusResult(team.getStatus());
    }

    /** 我的组队：{@code role=owner}（缺省）我发起的，{@code role=member} 我申请的（含待审批 / 已拒绝） */
    public MyTeamPage mine(long userId, String roleRaw, int page, int size) {
        String role = roleRaw == null || roleRaw.isBlank() ? "owner" : roleRaw.strip().toLowerCase();
        int currentPage = normalizePage(page);
        int pageSize = normalizeSize(size);
        long total;
        List<MyTeamItem> items;
        if ("owner".equals(role)) {
            total = teamRepository.countOfOwner(userId);
            items = teamRepository.pageOfOwner(userId, offsetOf(currentPage, pageSize), pageSize);
        } else if ("member".equals(role)) {
            total = teamRepository.countOfMember(userId);
            items = teamRepository.pageOfMember(userId, offsetOf(currentPage, pageSize), pageSize);
        } else {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
        return new MyTeamPage(items, total, currentPage, pageSize);
    }

    private Team requireTeam(long teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new ApiException(ResultCode.TEAM_NOT_FOUND));
    }

    private static void requireOwner(Team team, long userId) {
        if (team.getOwnerId() != userId) {
            throw new ApiException(ResultCode.FORBIDDEN);
        }
    }

    private static boolean parseDecision(String raw) {
        if (raw == null) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
        return switch (raw.strip().toUpperCase()) {
            case "ACCEPT" -> true;
            case "REJECT" -> false;
            default -> throw new ApiException(ResultCode.INVALID_PARAM);
        };
    }

    private static int normalizePage(int page) {
        return Math.max(page, 1);
    }

    private static int normalizeSize(int size) {
        return size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
    }

    private static int offsetOf(int page, int size) {
        return (int) Math.min((long) (page - 1) * size, Integer.MAX_VALUE);
    }
}
