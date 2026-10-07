package com.campuslink.module.forum.web;

import com.campuslink.common.result.ApiResponse;
import com.campuslink.common.result.ErrorCodes;
import com.campuslink.common.result.ResultCode;
import com.campuslink.common.web.ApiDocs;
import com.campuslink.common.web.CurrentUser;
import com.campuslink.module.forum.application.ReportApplicationService;
import com.campuslink.module.forum.domain.gateway.PageResult;
import com.campuslink.module.forum.domain.model.Report;
import com.campuslink.module.forum.web.vo.PageVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 举报工单运营接口（F-ADMIN-003 / CR-082）：仅 SUPERADMIN（G9 由 requireRole 机器强制 +
 * 「每个管理端点自带 403 用例」约定互补，见 AGENTS.md 测试约定）。
 */
@Tag(name = "admin-report", description = "举报工单处理（仅 SUPERADMIN）")
@RestController
@RequestMapping("/api/v1/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {

    private final ReportApplicationService reportApplicationService;

    public record AdminReportVo(Long id, Long reporterId, String targetType, Long targetId, String reason,
                                String detail, String status, String resolution, Long handlerId,
                                java.time.Instant createdAt, java.time.Instant closedAt) {

        public static AdminReportVo from(Report r) {
            return new AdminReportVo(r.id(), r.reporterId(), r.targetType(), r.targetId(), r.reason(),
                    r.detail(), r.status(), r.resolution(), r.handlerId(), r.createdAt(), r.closedAt());
        }
    }

    public record CloseCommand(
            @NotBlank @Size(max = 16, message = "action 仅支持 REMOVE / DISMISS") String action,
            @Size(max = 512, message = "处理结论最长 512 字") String note) {
    }

    @Operation(summary = "工单分页（仅 SUPERADMIN）：status 可选 PENDING / CLOSED，缺省全部")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ResultCode.NOT_LOGGED_IN, ResultCode.FORBIDDEN})
    @GetMapping
    public ApiResponse<PageVo<AdminReportVo>> page(@RequestParam(required = false) String status,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size,
                                                   Authentication authentication) {
        CurrentUser.requireRole(authentication, "ROLE_SUPERADMIN");
        PageResult<Report> result = reportApplicationService.adminPage(status, page, size);
        return ApiResponse.ok(new PageVo<>(result.items().stream().map(AdminReportVo::from).toList(),
                result.total(), result.page(), result.size()));
    }

    @Operation(summary = "工单处理（仅 SUPERADMIN）：action=REMOVE 联动目标处置（下架 / 封禁），DISMISS 驳回；闭环 + 审计")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ResultCode.NOT_LOGGED_IN, ResultCode.FORBIDDEN, ResultCode.NOT_FOUND})
    @PutMapping("/{id}")
    public ApiResponse<Void> close(@PathVariable("id") long id,
                                   @Valid @RequestBody CloseCommand command,
                                   Authentication authentication) {
        long operatorId = CurrentUser.requireRole(authentication, "ROLE_SUPERADMIN");
        reportApplicationService.close(operatorId, id, command.action(), command.note());
        return ApiResponse.ok();
    }
}
