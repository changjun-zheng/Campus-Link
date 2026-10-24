package com.campuslink.module.account.web;

import com.campuslink.common.result.ApiResponse;
import com.campuslink.common.result.ErrorCodes;
import com.campuslink.common.result.ResultCode;
import com.campuslink.common.web.ApiDocs;
import com.campuslink.common.web.CurrentUser;
import com.campuslink.module.account.application.UserBlockApplicationService;
import com.campuslink.module.account.web.vo.BlockStateVo;
import com.campuslink.module.account.web.vo.BlockedUsersVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 拉黑 / 屏蔽（F-SOC-004 / CR-129）：四个**受保护**端点，身份只从令牌取。
 *
 * <p>被拉黑者无感知：拉黑 / 解除都不通知对方、也不返回"你是否被谁拉黑"。
 */
@Tag(name = "user", description = "用户")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserBlockController {

    private final UserBlockApplicationService userBlockApplicationService;

    @Operation(summary = "拉黑用户（需登录，幂等）：被拉黑者的内容、互动与通知对本人不再出现")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ResultCode.NOT_LOGGED_IN, ResultCode.USER_NOT_FOUND})
    @PostMapping("/{targetId}/block")
    public ApiResponse<Void> block(@PathVariable("targetId") Long targetId, Authentication authentication) {
        userBlockApplicationService.block(CurrentUser.requireId(authentication), targetId);
        return ApiResponse.ok();
    }

    @Operation(summary = "解除拉黑（需登录，幂等）：解除后历史内容与互动立即恢复")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ResultCode.NOT_LOGGED_IN, ResultCode.USER_NOT_FOUND})
    @DeleteMapping("/{targetId}/block")
    public ApiResponse<Void> unblock(@PathVariable("targetId") Long targetId, Authentication authentication) {
        userBlockApplicationService.unblock(CurrentUser.requireId(authentication), targetId);
        return ApiResponse.ok();
    }

    @Operation(summary = "我是否拉黑了该用户（需登录）：仅返回本人视角，不泄露反向关系")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ResultCode.NOT_LOGGED_IN, ResultCode.USER_NOT_FOUND})
    @GetMapping("/{targetId}/block-state")
    public ApiResponse<BlockStateVo> blockState(@PathVariable("targetId") Long targetId,
                                                Authentication authentication) {
        boolean blocked = userBlockApplicationService.isBlocked(CurrentUser.requireId(authentication), targetId);
        return ApiResponse.ok(new BlockStateVo(blocked));
    }

    @Operation(summary = "我的黑名单（需登录，分页，按拉黑时间倒序）")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ResultCode.NOT_LOGGED_IN})
    @GetMapping("/me/blocks")
    public ApiResponse<BlockedUsersVo> blockedUsers(@RequestParam(value = "page", defaultValue = "1") int page,
                                                    @RequestParam(value = "size", defaultValue = "20") int size,
                                                    Authentication authentication) {
        long userId = CurrentUser.requireId(authentication);
        int currentPage = Math.max(page, 1);
        int pageSize = Math.min(Math.max(size, 1), 100);
        return ApiResponse.ok(BlockedUsersVo.from(
                userBlockApplicationService.pageBlocked(userId, currentPage, pageSize), currentPage, pageSize));
    }
}
