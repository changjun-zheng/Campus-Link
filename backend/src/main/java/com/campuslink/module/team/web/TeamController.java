package com.campuslink.module.team.web;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ApiResponse;
import com.campuslink.common.result.ErrorCodes;
import com.campuslink.common.result.ResultCode;
import com.campuslink.common.web.ApiDocs;
import com.campuslink.common.web.CurrentUser;
import com.campuslink.common.web.PublicEndpoint;
import com.campuslink.module.team.application.TeamApplicationService;
import com.campuslink.module.team.application.cmd.CreateTeamCommand;
import com.campuslink.module.team.application.cmd.DecideApplicationCommand;
import com.campuslink.module.team.application.cmd.TeamResults.ApplicationPage;
import com.campuslink.module.team.application.cmd.TeamResults.MyTeamPage;
import com.campuslink.module.team.application.cmd.TeamResults.TeamPage;
import com.campuslink.module.team.application.cmd.UpdateTeamStatusCommand;
import com.campuslink.module.team.web.vo.MyTeamVo;
import com.campuslink.module.team.web.vo.PageVo;
import com.campuslink.module.team.web.vo.TeamApplicationVo;
import com.campuslink.module.team.web.vo.TeamApplyResultVo;
import com.campuslink.module.team.web.vo.TeamDetailVo;
import com.campuslink.module.team.web.vo.TeamStatusResultVo;
import com.campuslink.module.team.web.vo.TeamSummaryVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 组队广场接口（F-TEAM-001 / CR-137）。
 *
 * <p>鉴权约定同其他上下文（N-4 已闭环）：公开端点显式标 {@link PublicEndpoint}，
 * 受保护端点声明 {@link SecurityRequirement} 且真的调用 {@link CurrentUser}，
 * 由 {@code ArchitectureGuardTest} G7 机器校验。
 *
 * <p>⚠️ 方法名刻意避开既有 operationId：{@code listTeams} / {@code teamDetail} / {@code createTeam} /
 * {@code applyToTeam} / {@code listTeamApplications} / {@code decideApplication} / {@code updateTeamStatus} /
 * {@code myTeams} 均与既有 Controller 不重名（CR-130 / CR-132 两次撞车教训）。
 */
@Tag(name = "team", description = "组队广场")
@RestController
@RequestMapping("/api/v1/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamApplicationService teamApplicationService;

    @Operation(summary = "组队广场列表（公开）：status 缺省 RECRUITING，可按 direction 过滤；含发起人昵称与已加入人数，不含联系方式")
    @PublicEndpoint
    @GetMapping
    public ApiResponse<PageVo<TeamSummaryVo>> listTeams(
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        TeamPage result = teamApplicationService.list(direction, status, page, size);
        return ApiResponse.ok(new PageVo<>(result.list().stream().map(TeamSummaryVo::from).toList(),
                result.total(), result.page(), result.size()));
    }

    @Operation(summary = "我的组队（需登录）：role=owner（缺省）我发起的、role=member 我申请的（含待审批 / 已拒绝）")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ ResultCode.NOT_LOGGED_IN })
    @GetMapping("/mine")
    public ApiResponse<PageVo<MyTeamVo>> myTeams(Authentication authentication,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        long userId = CurrentUser.requireId(authentication);
        MyTeamPage result = teamApplicationService.mine(userId, role, page, size);
        return ApiResponse.ok(new PageVo<>(result.list().stream().map(MyTeamVo::from).toList(),
                result.total(), result.page(), result.size()));
    }

    @Operation(summary = "组队详情（公开）：登录者附带我的申请状态；联系方式仅发起人与已加入成员可见")
    @PublicEndpoint
    @ErrorCodes({ ResultCode.TEAM_NOT_FOUND })
    @GetMapping("/{id}")
    public ApiResponse<TeamDetailVo> teamDetail(@PathVariable("id") long id, Authentication authentication) {
        Long viewerId = CurrentUser.optionalId(authentication);
        return ApiResponse.ok(TeamDetailVo.from(teamApplicationService.detail(viewerId, id)));
    }

    @Operation(summary = "发布招募（需登录）：方向为固定枚举，人数 2~20（含发起人），联系方式仅成员可见；标题 / 简介 / 目标赛事走机审")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ ResultCode.NOT_LOGGED_IN, ResultCode.USER_BANNED, ResultCode.ACCOUNT_DEACTIVATED,
            ResultCode.MODERATION_BLOCKED, ResultCode.MODERATION_UNAVAILABLE })
    @PostMapping
    public ApiResponse<TeamDetailVo> createTeam(@Valid @RequestBody CreateTeamCommand command,
            Authentication authentication) {
        long userId = CurrentUser.requireId(authentication);
        return ApiResponse.ok(TeamDetailVo.from(teamApplicationService.create(userId, command)));
    }

    @Operation(summary = "申请加入（需登录）：申请后待发起人审批；不能申请自己的组队，已满员 / 已关闭 / 已申请均被拒")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ ResultCode.NOT_LOGGED_IN, ResultCode.USER_BANNED, ResultCode.ACCOUNT_DEACTIVATED,
            ResultCode.TEAM_NOT_FOUND, ResultCode.TEAM_NOT_RECRUITING, ResultCode.TEAM_FULL,
            ResultCode.TEAM_APPLICATION_EXISTS, ResultCode.CANNOT_APPLY_OWN_TEAM })
    @PostMapping("/{id}/applications")
    public ApiResponse<TeamApplyResultVo> applyToTeam(@PathVariable("id") long id,
            Authentication authentication) {
        long userId = CurrentUser.requireId(authentication);
        return ApiResponse.ok(TeamApplyResultVo.from(teamApplicationService.apply(userId, id)));
    }

    @Operation(summary = "待审批申请列表（需登录，仅发起人）：只列 PENDING，按申请时间升序")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ ResultCode.NOT_LOGGED_IN, ResultCode.TEAM_NOT_FOUND, ResultCode.FORBIDDEN })
    @GetMapping("/{id}/applications")
    public ApiResponse<PageVo<TeamApplicationVo>> listTeamApplications(@PathVariable("id") long id,
            Authentication authentication,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        long userId = CurrentUser.requireId(authentication);
        ApplicationPage result = teamApplicationService.listApplications(userId, id, page, size);
        return ApiResponse.ok(new PageVo<>(result.list().stream().map(TeamApplicationVo::from).toList(),
                result.total(), result.page(), result.size()));
    }

    @Operation(summary = "审批申请（需登录，仅发起人）：decision=ACCEPT / REJECT；接受后人数达上限自动满员")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ ResultCode.NOT_LOGGED_IN, ResultCode.TEAM_NOT_FOUND, ResultCode.FORBIDDEN,
            ResultCode.TEAM_APPLICATION_NOT_FOUND, ResultCode.TEAM_FULL, ResultCode.TEAM_NOT_RECRUITING })
    @PutMapping("/{id}/applications/{userId}")
    public ApiResponse<TeamStatusResultVo> decideApplication(@PathVariable("id") long id,
            @PathVariable("userId") long applicantId,
            @Valid @RequestBody DecideApplicationCommand command,
            Authentication authentication) {
        long ownerId = CurrentUser.requireId(authentication);
        return ApiResponse.ok(TeamStatusResultVo.from(
                teamApplicationService.decide(ownerId, id, applicantId, command.decision())));
    }

    @Operation(summary = "关闭组队（需登录，仅发起人）：幂等；关闭后不可重开")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ ResultCode.NOT_LOGGED_IN, ResultCode.TEAM_NOT_FOUND, ResultCode.FORBIDDEN })
    @PutMapping("/{id}/status")
    public ApiResponse<TeamStatusResultVo> updateTeamStatus(@PathVariable("id") long id,
            @Valid @RequestBody UpdateTeamStatusCommand command,
            Authentication authentication) {
        long ownerId = CurrentUser.requireId(authentication);
        if (!"CLOSED".equalsIgnoreCase(command.status().strip())) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
        return ApiResponse.ok(TeamStatusResultVo.from(teamApplicationService.close(ownerId, id)));
    }
}
