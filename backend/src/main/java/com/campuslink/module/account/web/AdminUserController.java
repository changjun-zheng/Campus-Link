package com.campuslink.module.account.web;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ApiResponse;
import com.campuslink.common.result.ErrorCodes;
import com.campuslink.common.result.ResultCode;
import com.campuslink.common.web.ApiDocs;
import com.campuslink.common.web.CurrentUser;
import com.campuslink.module.account.application.AccountApplicationService;
import com.campuslink.module.account.domain.model.AccountStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户治理接口（F-ADMIN 治理 / CR-082）：封禁 / 解封，仅 SUPERADMIN（G9：requireRole 机器强制，
 * 配套「每个管理端点自带 403 用例」约定，见 AGENTS.md 测试约定）。
 */
@Tag(name = "admin-user", description = "用户治理（仅 SUPERADMIN）")
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AccountApplicationService accountApplicationService;

    public record StatusCommand(@NotBlank(message = "status 仅支持 ACTIVE / BANNED") String status) {
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
}
