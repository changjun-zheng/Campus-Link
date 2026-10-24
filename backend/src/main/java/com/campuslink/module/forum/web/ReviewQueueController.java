package com.campuslink.module.forum.web;

import com.campuslink.common.result.ApiResponse;
import com.campuslink.common.result.ErrorCodes;
import com.campuslink.common.result.ResultCode;
import com.campuslink.common.web.ApiDocs;
import com.campuslink.common.web.CurrentUser;
import com.campuslink.module.forum.application.ReviewQueueApplicationService;
import com.campuslink.module.forum.web.vo.ReviewQueueVo;
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
 * 人工巡查队列接口（F-SAFE-004 / CR-133）：仅 SUPERADMIN。
 *
 * <p>只读端点——下架 / 恢复复用既有 {@code AdminModerationController} 的两个 PUT；
 * 后者已有「匿名 401 / 角色不足 403」用例与 G9 守护，本端点只补发现入口。
 */
@Tag(name = "admin-review", description = "运营后台 · 内容巡查（仅 SUPERADMIN）")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class ReviewQueueController {

    private final ReviewQueueApplicationService reviewQueueApplicationService;

    @Operation(summary = "内容巡查队列（仅 SUPERADMIN）：帖子 / 楼层按发布时间倒序；只列未删内容，已下架仍列出")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ResultCode.NOT_LOGGED_IN, ResultCode.FORBIDDEN})
    @GetMapping("/review-queue")
    public ApiResponse<ReviewQueueVo> reviewQueue(@RequestParam(value = "kind", defaultValue = "post") String kind,
                                                  @RequestParam(value = "page", defaultValue = "1") int page,
                                                  @RequestParam(value = "size", defaultValue = "20") int size,
                                                  Authentication authentication) {
        CurrentUser.requireRole(authentication, "ROLE_SUPERADMIN");
        return ApiResponse.ok(ReviewQueueVo.from(reviewQueueApplicationService.page(kind, page, size)));
    }
}
