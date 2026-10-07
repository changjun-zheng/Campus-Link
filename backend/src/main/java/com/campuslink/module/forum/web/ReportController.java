package com.campuslink.module.forum.web;

import com.campuslink.common.result.ApiResponse;
import com.campuslink.common.result.ErrorCodes;
import com.campuslink.common.result.ResultCode;
import com.campuslink.common.web.ApiDocs;
import com.campuslink.common.web.CurrentUser;
import com.campuslink.module.forum.application.ReportApplicationService;
import com.campuslink.module.forum.domain.model.Report;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "report", description = "举报（F-SAFE-002）")
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportApplicationService reportApplicationService;

    public record ReportCommand(
            @NotBlank @Size(max = 16, message = "targetType 不合法") String targetType,
            @NotNull Long targetId,
            @NotBlank @Size(max = 32, message = "举报理由不合法") String reason,
            @Size(max = 1024, message = "补充说明最长 1024 字") String detail) {
    }

    @Operation(summary = "提交举报（需登录）：24 小时内对同一对象重复举报 → 429 / 2012")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ResultCode.NOT_LOGGED_IN, ResultCode.REPORT_DUPLICATED})
    @PostMapping
    public ApiResponse<ReportVo> submit(@Valid @RequestBody ReportCommand command,
                                        Authentication authentication) {
        long userId = CurrentUser.requireId(authentication);
        Report report = reportApplicationService.submit(userId, command.targetType(), command.targetId(),
                command.reason(), command.detail());
        return ApiResponse.ok(new ReportVo(report.id(), report.targetType(), report.targetId(), report.status()));
    }

    /** 提交回执：工单 id 与状态，前端提示「已提交，运营将尽快处理」 */
    public record ReportVo(Long id, String targetType, Long targetId, String status) {
    }
}
