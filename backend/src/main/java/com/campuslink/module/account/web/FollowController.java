package com.campuslink.module.account.web;

import com.campuslink.common.result.ApiResponse;
import com.campuslink.common.result.ErrorCodes;
import com.campuslink.common.result.ResultCode;
import com.campuslink.common.web.ApiDocs;
import com.campuslink.common.web.CurrentUser;
import com.campuslink.module.account.application.FollowApplicationService;
import com.campuslink.module.account.web.vo.FollowUsersVo;
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
 * 关注接口（CR-074 / F-SOC-002 提前）：三端点均需登录（follow-state 也是登录视角的"我是否已关注"）；
 * 角色与存在性校验经 {@link CurrentUser} + application（N-4 约定：web 只判登录，资源规则在业务侧）。
 */
@Tag(name = "follow", description = "关注关系")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class FollowController {

    private final FollowApplicationService followApplicationService;

    @Operation(summary = "关注用户（需登录；不能关注自己 2009，重复关注幂等）")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ResultCode.NOT_LOGGED_IN, ResultCode.USER_NOT_FOUND, ResultCode.CANNOT_FOLLOW_SELF})
    @PostMapping("/{targetId}/follow")
    public ApiResponse<Void> follow(@PathVariable("targetId") long targetId, Authentication authentication) {
        followApplicationService.follow(CurrentUser.requireId(authentication), targetId);
        return ApiResponse.ok();
    }

    @Operation(summary = "取消关注（需登录；未关注时幂等）")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ResultCode.NOT_LOGGED_IN, ResultCode.USER_NOT_FOUND})
    @DeleteMapping("/{targetId}/follow")
    public ApiResponse<Void> unfollow(@PathVariable("targetId") long targetId, Authentication authentication) {
        followApplicationService.unfollow(CurrentUser.requireId(authentication), targetId);
        return ApiResponse.ok();
    }

    @Operation(summary = "关注状态（需登录）：following 为登录者视角，fanCount / followCount 为目标的粉丝数与关注数")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ResultCode.NOT_LOGGED_IN, ResultCode.USER_NOT_FOUND})
    @GetMapping("/{targetId}/follow-state")
    public ApiResponse<FollowStateVo> state(@PathVariable("targetId") long targetId, Authentication authentication) {
        long viewerId = CurrentUser.requireId(authentication);
        FollowApplicationService.FollowState s = followApplicationService.state(viewerId, targetId);
        return ApiResponse.ok(new FollowStateVo(s.following(), s.followerCount(), s.followeeCount()));
    }

    /**
     * 粉丝列表（F-SOC-002 补齐 / CR-130）：受保护而不是公开——拉黑过滤需要 viewer 身份
     * （匿名没有"谁拉黑了谁"的视角），也避免匿名批量枚举用户；目标不存在或已注销统一 2007，
     * 与 {@code GET /users/{id}} 同口径。
     */
    @Operation(summary = "粉丝列表（需登录，分页，按关注时间倒序）：条目剔除已注销账号与登录者已拉黑账号")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ResultCode.NOT_LOGGED_IN, ResultCode.USER_NOT_FOUND})
    @GetMapping("/{targetId}/followers")
    public ApiResponse<FollowUsersVo> followers(@PathVariable("targetId") long targetId,
                                                @RequestParam(value = "page", defaultValue = "1") int page,
                                                @RequestParam(value = "size", defaultValue = "20") int size,
                                                Authentication authentication) {
        long viewerId = CurrentUser.requireId(authentication);
        return ApiResponse.ok(FollowUsersVo.from(
                followApplicationService.pageFollowers(viewerId, targetId, page, size)));
    }

    @Operation(summary = "关注列表（需登录，分页，按关注时间倒序）：过滤口径同粉丝列表")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ResultCode.NOT_LOGGED_IN, ResultCode.USER_NOT_FOUND})
    @GetMapping("/{targetId}/following")
    public ApiResponse<FollowUsersVo> followingUsers(@PathVariable("targetId") long targetId,
                                                     @RequestParam(value = "page", defaultValue = "1") int page,
                                                     @RequestParam(value = "size", defaultValue = "20") int size,
                                                     Authentication authentication) {
        long viewerId = CurrentUser.requireId(authentication);
        return ApiResponse.ok(FollowUsersVo.from(
                followApplicationService.pageFollowing(viewerId, targetId, page, size)));
    }

    /** web 视图：字段名对前端契约（following / fanCount / followCount） */
    public record FollowStateVo(boolean following, long fanCount, long followCount) {

        public static FollowStateVo from(FollowApplicationService.FollowState s) {
            return new FollowStateVo(s.following(), s.followerCount(), s.followeeCount());
        }
    }
}
