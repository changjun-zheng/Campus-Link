package com.campuslink.module.forum.web;

import com.campuslink.common.result.ApiResponse;
import com.campuslink.common.result.ErrorCodes;
import com.campuslink.common.result.ResultCode;
import com.campuslink.common.web.ApiDocs;
import com.campuslink.common.web.CurrentUser;
import com.campuslink.module.forum.application.AdminStatsApplicationService;
import com.campuslink.module.forum.web.vo.AdminDashboardVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运营看板接口（F-ADMIN-005 / CR-131）：仅 SUPERADMIN。
 *
 * <p>与既有 admin 端点同规矩：{@code requireRole} 是角色线唯一的运行时判定（G9 守护其存在、
 * 端点自带 403 用例守护其取值得当），路径级框架只区分登录 / 未登录。
 */
@Tag(name = "admin-dashboard", description = "运营后台 · 数据看板（仅 SUPERADMIN）")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminStatsApplicationService adminStatsApplicationService;

    @Operation(summary = "数据看板（仅 SUPERADMIN）：用户 / 帖子 / 楼层 / 举报四组计数；今日按 UTC 日界")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ResultCode.NOT_LOGGED_IN, ResultCode.FORBIDDEN})
    @GetMapping("/dashboard")
    public ApiResponse<AdminDashboardVo> dashboard(Authentication authentication) {
        CurrentUser.requireRole(authentication, "ROLE_SUPERADMIN");
        return ApiResponse.ok(AdminDashboardVo.from(adminStatsApplicationService.dashboard()));
    }
}
