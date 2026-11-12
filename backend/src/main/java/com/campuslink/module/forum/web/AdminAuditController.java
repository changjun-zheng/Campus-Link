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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * 操作审计接口（CR-215 / CR-216）：仅 SUPERADMIN 可读。
 *
 * <p>只读端点——审计写入仍只经 {@code common.audit.AuditService}，本端点不提供任何修改动作。
 * OPS 不可见：审计包含跨用户处置与角色变更记录，按「权限管理」归 SUPERADMIN。
 * G9 由 {@code requireRole} 机器强制，端点自带「匿名 401 / USER·OPS 403」用例。
 *
 * <p>导出（CR-216）返回 {@code text/csv} 原始字节，不走 {@code ApiResponse} 外壳；
 * 错误仍由 {@code GlobalExceptionHandler} 产出 JSON，前端 `getBinary` 按同一语义解析。
 */
@Tag(name = "admin-audit", description = "运营后台 · 操作审计（仅 SUPERADMIN）")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminAuditController {

    private final AdminAuditApplicationService adminAuditApplicationService;

    @Operation(summary = "操作审计分页（仅 SUPERADMIN）：action / targetType 精确筛选、actorId / targetId 筛选、"
            + "days 限 7/30/90；按时间倒序，含操作者昵称与目标对象")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ResultCode.NOT_LOGGED_IN, ResultCode.FORBIDDEN})
    @GetMapping("/audit-logs")
    public ApiResponse<PageVo<AdminAuditVo>> page(@RequestParam(required = false) String action,
                                                  @RequestParam(required = false) Long actorId,
                                                  @RequestParam(required = false) String targetType,
                                                  @RequestParam(required = false) Long targetId,
                                                  @RequestParam(required = false) Integer days,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size,
                                                  Authentication authentication) {
        CurrentUser.requireRole(authentication, "ROLE_SUPERADMIN");
        PageResult<AdminAuditApplicationService.AuditItem> result =
                adminAuditApplicationService.page(action, actorId, targetType, targetId, days, page, size);
        return ApiResponse.ok(new PageVo<>(result.items().stream().map(AdminAuditVo::from).toList(),
                result.total(), result.page(), result.size()));
    }

    @Operation(summary = "操作审计 CSV 导出（仅 SUPERADMIN）：与分页同筛选；UTF-8 BOM、最多 5000 行，超出 1001")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ResultCode.NOT_LOGGED_IN, ResultCode.FORBIDDEN})
    @GetMapping(value = "/audit-logs/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String action,
                                         @RequestParam(required = false) Long actorId,
                                         @RequestParam(required = false) String targetType,
                                         @RequestParam(required = false) Long targetId,
                                         @RequestParam(required = false) Integer days,
                                         Authentication authentication) {
        CurrentUser.requireRole(authentication, "ROLE_SUPERADMIN");
        AdminAuditApplicationService.AuditExport export =
                adminAuditApplicationService.export(action, actorId, targetType, targetId, days);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + export.filename() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(export.content());
    }
}
