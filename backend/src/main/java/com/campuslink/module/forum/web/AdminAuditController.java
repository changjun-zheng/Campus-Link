package com.campuslink.module.forum.web;

import com.campuslink.common.result.ApiResponse;
import com.campuslink.common.result.ErrorCodes;
import com.campuslink.common.result.ResultCode;
import com.campuslink.common.web.ApiDocs;
import com.campuslink.common.web.CurrentUser;
import com.campuslink.module.forum.application.AdminAuditApplicationService;
import com.campuslink.module.forum.domain.gateway.PageResult;
import com.campuslink.module.forum.web.vo.AdminAuditVo;
import com.campuslink.module.forum.web.vo.PageVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作审计接口（CR-215）：仅 SUPERADMIN 可读。
 *
 * <p>只读端点——审计写入仍只经 {@code common.audit.AuditService}，本端点不提供任何修改动作。
 * OPS 不可见：审计包含跨用户处置与角色变更记录，按「权限管理」归 SUPERADMIN。
 * G9 由 {@code requireRole} 机器强制，端点自带「匿名 401 / USER·OPS 403」用例。
 */
@Tag(name = "admin-audit", description = "运营后台 · 操作审计（仅 SUPERADMIN）")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminAuditController {

    private final AdminAuditApplicationService adminAuditApplicationService;

    @Operation(summary = "操作审计分页（仅 SUPERADMIN）：action 精确筛选、actorId 筛选、days 限 7/30/90；"
            + "按时间倒序，含操作者昵称与目标对象")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ResultCode.NOT_LOGGED_IN, ResultCode.FORBIDDEN})
    @GetMapping("/audit-logs")
    public ApiResponse<PageVo<AdminAuditVo>> page(@RequestParam(required = false) String action,
                                                  @RequestParam(required = false) Long actorId,
                                                  @RequestParam(required = false) Integer days,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size,
                                                  Authentication authentication) {
        CurrentUser.requireRole(authentication, "ROLE_SUPERADMIN");
        PageResult<AdminAuditApplicationService.AuditItem> result =
                adminAuditApplicationService.page(action, actorId, days, page, size);
        return ApiResponse.ok(new PageVo<>(result.items().stream().map(AdminAuditVo::from).toList(),
                result.total(), result.page(), result.size()));
    }
}
