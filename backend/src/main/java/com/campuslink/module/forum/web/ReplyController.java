package com.campuslink.module.forum.web;

import com.campuslink.common.result.ApiResponse;
import com.campuslink.common.result.ErrorCodes;
import com.campuslink.common.result.ResultCode;
import com.campuslink.common.web.ApiDocs;
import com.campuslink.common.web.CurrentUser;
import com.campuslink.common.web.PublicEndpoint;
import com.campuslink.module.forum.application.ForumQueryApplicationService;
import com.campuslink.module.forum.application.InteractionApplicationService;
import com.campuslink.module.forum.application.ReplyApplicationService;
import com.campuslink.module.forum.application.cmd.ForumResults.InteractionResult;
import com.campuslink.module.forum.application.cmd.ForumResults.PublishedReply;
import com.campuslink.module.forum.application.cmd.ForumResults.ReplyItem;
import com.campuslink.module.forum.application.cmd.PublishReplyCommand;
import com.campuslink.module.forum.domain.gateway.PageResult;
import com.campuslink.module.forum.web.vo.PageVo;
import com.campuslink.module.forum.web.vo.ReplyVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 楼层接口（web 层）。
 *
 * <p>鉴权约定同 {@link PostController}（N-4 已闭环，CR-028）：公开端点标 {@link PublicEndpoint}、
 * 受保护端点声明 {@code @SecurityRequirement} 并调用 {@link CurrentUser}，由 {@code ArchitectureGuardTest} 机器校验。
 */
@Tag(name = "reply", description = "楼层回复")
@RestController
@RequestMapping("/api/v1/posts/{postId}/replies")
@RequiredArgsConstructor
public class ReplyController {

    private final ForumQueryApplicationService forumQueryService;
    private final ReplyApplicationService replyApplicationService;
    private final InteractionApplicationService interactionApplicationService;

    @Operation(summary = "楼层列表（公开）：按 floor_no 升序；楼层 = 回复序号，帖子本体不占楼层号；登录请求附带各楼层 likedByMe，匿名时恒 false")
    @ErrorCodes({ResultCode.NOT_FOUND})
    @PublicEndpoint
    @GetMapping
    public ApiResponse<PageVo<ReplyVo>> list(@PathVariable("postId") Long postId,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size,
                                             Authentication authentication) {
        PageResult<ReplyItem> result = forumQueryService.listReplies(postId, page, size,
                CurrentUser.optionalId(authentication));
        return ApiResponse.ok(new PageVo<>(result.items().stream().map(ReplyVo::from).toList(),
                result.total(), result.page(), result.size()));
    }

    @Operation(summary = "回帖（需登录）：返回本次楼层号；可带 quotedReplyId 引用同帖楼层（F-009 / CR-085），目标不可见或跨帖一律 404 / 3001")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ResultCode.NOT_LOGGED_IN, ResultCode.NOT_FOUND, ResultCode.USER_BANNED,
            ResultCode.ACCOUNT_DEACTIVATED, ResultCode.MODERATION_BLOCKED, ResultCode.MODERATION_UNAVAILABLE})
    @PostMapping
    public ApiResponse<PublishedReply> reply(@PathVariable("postId") Long postId,
                                             @Valid @RequestBody PublishReplyCommand command,
                                             Authentication authentication) {
        long userId = CurrentUser.requireId(authentication);
        return ApiResponse.ok(replyApplicationService.reply(postId, userId, command));
    }

    @Operation(summary = "点赞 / 取消点赞楼层（需登录，toggle）：active 为操作后状态，count 为最新点赞数")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ResultCode.NOT_LOGGED_IN, ResultCode.NOT_FOUND})
    @PostMapping("/{replyId}/like")
    public ApiResponse<InteractionResult> like(@PathVariable("postId") Long postId,
                                               @PathVariable("replyId") Long replyId,
                                               Authentication authentication) {
        long userId = CurrentUser.requireId(authentication);
        return ApiResponse.ok(interactionApplicationService.toggleReplyLike(userId, postId, replyId));
    }
}
