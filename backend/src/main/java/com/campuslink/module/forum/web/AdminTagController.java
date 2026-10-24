package com.campuslink.module.forum.web;

import com.campuslink.common.result.ApiResponse;
import com.campuslink.common.result.ErrorCodes;
import com.campuslink.common.result.ResultCode;
import com.campuslink.common.web.ApiDocs;
import com.campuslink.common.web.CurrentUser;
import com.campuslink.module.forum.application.AdminTagApplicationService;
import com.campuslink.module.forum.web.vo.AdminTagsVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运营后台标签管理接口（F-ADMIN-004 标签侧 / CR-132）：仅 SUPERADMIN。
 *
 * <p>版块按 F-FORUM-001 固定 6 个、不支持增删，故本 PRD 条目只有标签侧可执行。
 * 与既有 admin 端点同规矩：{@code requireRole} 是角色线唯一运行时判定（G9 守护其存在、
 * 端点自带 403 用例守护取值得当）。
 */
@Tag(name = "admin-tags", description = "运营后台 · 标签管理（仅 SUPERADMIN）")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminTagController {

    private final AdminTagApplicationService adminTagApplicationService;

    @Operation(summary = "标签列表（仅 SUPERADMIN）：分页 + 关键字 + 排序（use_count 降序 / name 升序）")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ResultCode.NOT_LOGGED_IN, ResultCode.FORBIDDEN})
    @GetMapping("/tags")
    public ApiResponse<AdminTagsVo> tags(@RequestParam(value = "keyword", required = false) String keyword,
                                         @RequestParam(value = "sort", defaultValue = "use_count") String sort,
                                         @RequestParam(value = "page", defaultValue = "1") int page,
                                         @RequestParam(value = "size", defaultValue = "20") int size,
                                         Authentication authentication) {
        CurrentUser.requireRole(authentication, "ROLE_SUPERADMIN");
        return ApiResponse.ok(AdminTagsVo.from(adminTagApplicationService.page(keyword, sort, page, size)));
    }

    @Operation(summary = "删除标签（仅 SUPERADMIN）：删关联 + 清 posts.tags 冗余列 + 删行，记 TAG_DELETE 审计")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ResultCode.NOT_LOGGED_IN, ResultCode.FORBIDDEN, ResultCode.NOT_FOUND})
    @DeleteMapping("/tags/{id}")
    public ApiResponse<Void> deleteTag(@PathVariable("id") long id, Authentication authentication) {
        long operatorId = CurrentUser.requireRole(authentication, "ROLE_SUPERADMIN");
        adminTagApplicationService.delete(operatorId, id);
        return ApiResponse.ok();
    }
}
