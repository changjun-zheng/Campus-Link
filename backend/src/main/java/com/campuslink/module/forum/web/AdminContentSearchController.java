package com.campuslink.module.forum.web;

import com.campuslink.common.result.ApiResponse;
import com.campuslink.common.result.ErrorCodes;
import com.campuslink.common.result.ResultCode;
import com.campuslink.common.web.ApiDocs;
import com.campuslink.common.web.CurrentUser;
import com.campuslink.module.forum.application.AdminContentSearchApplicationService;
import com.campuslink.module.forum.web.vo.AdminContentSearchVo;
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
 * 后台内容搜索接口（F-ADMIN-002 / CR-141）：仅 SUPERADMIN。
 *
 * <p>只读端点——下架 / 恢复复用既有 {@code AdminModerationController} 的两个 PUT，
 * 后者已有「匿名 401 / 角色不足 403」用例与 G9 守护；本端点只补"按关键词 / 用户 / 时间发现内容"。
 */
@Tag(name = "admin-content", description = "运营后台 · 内容搜索（仅 SUPERADMIN）")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminContentSearchController {

    private final AdminContentSearchApplicationService adminContentSearchApplicationService;

    @Operation(summary = "内容搜索（仅 SUPERADMIN）：type=post|reply，keyword 搜标题 / 正文，"
            + "userId 或 authorKeyword 筛作者，days 限 7/30/90；只列未删内容，已下架仍列出")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ResultCode.NOT_LOGGED_IN, ResultCode.FORBIDDEN})
    @GetMapping("/contents")
    public ApiResponse<AdminContentSearchVo> search(
            @RequestParam(value = "type", defaultValue = "post") String type,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "authorKeyword", required = false) String authorKeyword,
            @RequestParam(value = "days", required = false) Integer days,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            Authentication authentication) {
        CurrentUser.requireRole(authentication, "ROLE_SUPERADMIN");
        return ApiResponse.ok(AdminContentSearchVo.from(
                adminContentSearchApplicationService.search(type, keyword, userId, authorKeyword,
                        days, page, size)));
    }
}
