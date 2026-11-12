package com.campuslink.module.account.web;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ApiResponse;
import com.campuslink.common.result.ErrorCodes;
import com.campuslink.common.result.ResultCode;
import com.campuslink.common.web.ApiDocs;
import com.campuslink.common.web.CurrentUser;
import com.campuslink.module.account.application.AccountApplicationService;
import com.campuslink.module.account.domain.model.AccountStatus;
import com.campuslink.module.account.web.vo.AdminUserSearchVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户治理接口（F-ADMIN 治理 / CR-082、CR-196）：封禁 / 解封与运营角色授予，仅 SUPERADMIN
 * （G9：requireRole 机器强制，配套「每个管理端点自带 403 用例」约定，见 AGENTS.md 测试约定）。
 */
@Tag(name = "admin-user", description = "用户治理（仅 SUPERADMIN）")
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AccountApplicationService accountApplicationService;

    public record StatusCommand(@NotBlank(message = "status 仅支持 ACTIVE / BANNED") String status) {
    }

    public record RoleCommand(@NotBlank(message = "role 仅支持 OPS") String role) {
    }

    /** 警告（CR-209）：原因必填，作为通知正文与审计摘要 */
    public record WarnCommand(@NotBlank(message = "警告原因不能为空") @Size(max = 200, message = "警告原因最长 200 字")
                              String reason) {
    }

    /** 禁言（CR-209）：1~720 小时；永久处置请用封禁 */
    public record MuteCommand(@Min(value = 1, message = "禁言时长至少 1 小时")
                              @Max(value = 720, message = "禁言时长最长 720 小时")
                              int hours,
                              @NotBlank(message = "禁言原因不能为空") @Size(max = 200, message = "禁言原因最长 200 字")
                              String reason) {
    }

    @Operation(summary = "用户检索（仅 SUPERADMIN）：keyword 为纯数字时按用户 id 精确查，否则按昵称关键字；"
            + "最多 50 条，truncated 表示还有更多；只返回账号基础状态")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ResultCode.NOT_LOGGED_IN, ResultCode.FORBIDDEN})
    @GetMapping
    public ApiResponse<AdminUserSearchVo> searchUsers(@RequestParam("keyword") String keyword,
                                                      Authentication authentication) {
        CurrentUser.requireRole(authentication, "ROLE_SUPERADMIN");
        return ApiResponse.ok(AdminUserSearchVo.from(accountApplicationService.adminSearchUsers(keyword)));
    }

    @Operation(summary = "封禁 / 解封（仅 SUPERADMIN）：BANNED 即时生效（登录拒绝、发帖回帖拒绝），全程审计")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ResultCode.NOT_LOGGED_IN, ResultCode.FORBIDDEN, ResultCode.USER_NOT_FOUND})
    @PutMapping("/{id}/status")
    public ApiResponse<Void> setStatus(@PathVariable("id") long id,
                                       @Valid @RequestBody StatusCommand command,
                                       Authentication authentication) {
        long operatorId = CurrentUser.requireRole(authentication, "ROLE_SUPERADMIN");
        AccountStatus target = switch (command.status()) {
            case "BANNED" -> AccountStatus.BANNED;
            case "ACTIVE" -> AccountStatus.ACTIVE;
            default -> throw new ApiException(ResultCode.INVALID_PARAM);
        };
        accountApplicationService.adminSetStatus(operatorId, id, target);
        return ApiResponse.ok();
    }

    @Operation(summary = "授予运营角色（仅 SUPERADMIN）：仅允许 USER → OPS，幂等；目标用户需重新登录后生效")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ResultCode.NOT_LOGGED_IN, ResultCode.FORBIDDEN, ResultCode.USER_NOT_FOUND})
    @PutMapping("/{id}/role")
    public ApiResponse<Void> promoteToOps(@PathVariable("id") long id,
                                          @Valid @RequestBody RoleCommand command,
                                          Authentication authentication) {
        long operatorId = CurrentUser.requireRole(authentication, "ROLE_SUPERADMIN");
        if (!"OPS".equals(command.role())) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
        accountApplicationService.adminPromoteToOps(operatorId, id);
        return ApiResponse.ok();
    }

    @Operation(summary = "撤销运营角色（仅 SUPERADMIN）：仅允许 OPS → USER，幂等；撤销即时吊销目标旧 token，"
            + "重新登录后为普通用户")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ResultCode.NOT_LOGGED_IN, ResultCode.FORBIDDEN, ResultCode.USER_NOT_FOUND})
    @DeleteMapping("/{id}/role")
    public ApiResponse<Void> demoteToUser(@PathVariable("id") long id, Authentication authentication) {
        long operatorId = CurrentUser.requireRole(authentication, "ROLE_SUPERADMIN");
        accountApplicationService.adminDemoteToUser(operatorId, id);
        return ApiResponse.ok();
    }

    @Operation(summary = "警告用户（仅 SUPERADMIN）：向目标用户发送带原因的站内通知，不改账号状态；全程审计")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ResultCode.NOT_LOGGED_IN, ResultCode.FORBIDDEN, ResultCode.USER_NOT_FOUND})
    @PostMapping("/{id}/warn")
    public ApiResponse<Void> warn(@PathVariable("id") long id,
                                  @Valid @RequestBody WarnCommand command,
                                  Authentication authentication) {
        long operatorId = CurrentUser.requireRole(authentication, "ROLE_SUPERADMIN");
        accountApplicationService.adminWarn(operatorId, id, command.reason());
        return ApiResponse.ok();
    }

    @Operation(summary = "禁言用户（仅 SUPERADMIN）：1~720 小时，期间发帖 / 回帖 / 图片 / 组队写入返回 2019；到期自然解除")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ResultCode.NOT_LOGGED_IN, ResultCode.FORBIDDEN, ResultCode.USER_NOT_FOUND})
    @PutMapping("/{id}/mute")
    public ApiResponse<Void> mute(@PathVariable("id") long id,
                                  @Valid @RequestBody MuteCommand command,
                                  Authentication authentication) {
        long operatorId = CurrentUser.requireRole(authentication, "ROLE_SUPERADMIN");
        accountApplicationService.adminMute(operatorId, id, command.hours(), command.reason());
        return ApiResponse.ok();
    }

    @Operation(summary = "解除禁言（仅 SUPERADMIN）：幂等；本来未禁言时直接返回成功")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ResultCode.NOT_LOGGED_IN, ResultCode.FORBIDDEN, ResultCode.USER_NOT_FOUND})
    @DeleteMapping("/{id}/mute")
    public ApiResponse<Void> unmute(@PathVariable("id") long id, Authentication authentication) {
        long operatorId = CurrentUser.requireRole(authentication, "ROLE_SUPERADMIN");
        accountApplicationService.adminUnmute(operatorId, id);
        return ApiResponse.ok();
    }
}
