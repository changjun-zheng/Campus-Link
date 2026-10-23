package com.campuslink.module.forum.web;

import com.campuslink.common.result.ApiResponse;
import com.campuslink.common.result.ErrorCodes;
import com.campuslink.common.result.ResultCode;
import com.campuslink.common.web.ApiDocs;
import com.campuslink.common.web.CurrentUser;
import com.campuslink.common.web.IpUtil;
import com.campuslink.common.web.PublicEndpoint;
import com.campuslink.module.forum.application.ForumQueryApplicationService;
import com.campuslink.module.forum.application.InteractionApplicationService;
import com.campuslink.module.forum.application.PostApplicationService;
import com.campuslink.module.forum.application.PostViewCounter;
import com.campuslink.module.forum.application.cmd.AcceptReplyCommand;
import com.campuslink.module.forum.application.cmd.ForumResults.InteractionResult;
import com.campuslink.module.forum.application.cmd.ForumResults.MyPostSummary;
import com.campuslink.module.forum.application.cmd.ForumResults.PostDetail;
import com.campuslink.module.forum.application.cmd.ForumResults.PostSummary;
import com.campuslink.module.forum.application.cmd.ForumResults.PublishedPost;
import com.campuslink.module.forum.application.cmd.ForumResults.SimilarPostResult;
import com.campuslink.module.forum.application.cmd.PublishPostCommand;
import com.campuslink.module.forum.domain.gateway.PageResult;
import com.campuslink.module.forum.web.vo.MyPostSummaryVo;
import com.campuslink.module.forum.web.vo.PageVo;
import com.campuslink.module.forum.web.vo.PostDetailVo;
import com.campuslink.module.forum.web.vo.PostSummaryVo;
import com.campuslink.module.forum.web.vo.SimilarPostVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 帖子接口（web 层）：只做协议转换，用例逻辑在 application 层。
 *
 * <p>鉴权约定（N-4 已闭环，CR-028）：每个端点**必须且只能**声明 {@link PublicEndpoint}（公开）
 * 或 {@code @SecurityRequirement}（受保护）；受保护端点必须调用 {@link CurrentUser} 的统一入口。
 * 该约定由 {@code ArchitectureGuardTest} 机器校验——漏写即测试失败，不会静默变成公开接口。
 */
@Tag(name = "post", description = "帖子")
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final ForumQueryApplicationService forumQueryService;
    private final PostApplicationService postApplicationService;
    private final InteractionApplicationService interactionApplicationService;
    private final PostViewCounter viewCounter;

    @Operation(summary = "帖子列表（公开）：boardCode 缺省为全站；sort=latest（缺省，created_at DESC）或 hot（hot_score DESC，热榜），非法值 400；登录请求每条附带 likedByMe（匿名恒 false）")
    @ErrorCodes({ResultCode.NOT_FOUND})
    @PublicEndpoint
    @GetMapping
    public ApiResponse<PageVo<PostSummaryVo>> list(@RequestParam(required = false) String boardCode,
                                                   @RequestParam(defaultValue = "latest") String sort,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size,
                                                   Authentication authentication) {
        PageResult<PostSummary> result = forumQueryService.listPosts(boardCode, sort, page, size,
                CurrentUser.optionalId(authentication));
        return ApiResponse.ok(new PageVo<>(result.items().stream().map(PostSummaryVo::from).toList(),
                result.total(), result.page(), result.size()));
    }

    /**
     * 我的帖子（F-ACC-007b，需登录）：本人发帖时间倒序，条目带 {@code status}——
     * 平台下架的帖对作者本人可见并带标记，自删的帖对作者也不出现（口径写死在
     * {@code PostRepository#findPageByAuthor}）。
     *
     * <p><b>无身份参数</b>是这条接口能成立的前提：作者 id 只从令牌取，所以"把下架内容只给作者看"
     * 不依赖任何测试兜底（"仅本人"这类线没有机器强制，见问题清单 E-011 同类缺口）。
     */
    @Operation(summary = "我的帖子（需登录）：时间倒序分页；含被平台下架的条目（status=REMOVED，前端不可点），不含自己删除的")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ResultCode.NOT_LOGGED_IN})
    @GetMapping("/mine")
    public ApiResponse<PageVo<MyPostSummaryVo>> myPosts(Authentication authentication,
                                                        @RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        long userId = CurrentUser.requireId(authentication);
        PageResult<MyPostSummary> result = forumQueryService.listMyPosts(userId, page, size);
        return ApiResponse.ok(new PageVo<>(result.items().stream().map(MyPostSummaryVo::from).toList(),
                result.total(), result.page(), result.size()));
    }

    @Operation(summary = "站内搜索（公开）：keyword 2~50 字搜标题（ngram 全文）与标签；boardCode / days（仅 7、30、90）可筛选；按相关度 + 时间排序")
    @ErrorCodes({ResultCode.NOT_FOUND})
    @PublicEndpoint
    @GetMapping("/search")
    public ApiResponse<PageVo<PostSummaryVo>> search(@RequestParam String keyword,
                                                     @RequestParam(required = false) String boardCode,
                                                     @RequestParam(required = false) Integer days,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int size,
                                                     Authentication authentication) {
        PageResult<PostSummary> result = forumQueryService.searchPosts(keyword, boardCode, days, page, size,
                CurrentUser.optionalId(authentication));
        return ApiResponse.ok(new PageVo<>(result.items().stream().map(PostSummaryVo::from).toList(),
                result.total(), result.page(), result.size()));
    }

    @Operation(summary = "相似帖子推荐（受保护）——发帖时根据标题检索已有的相似问题")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ResultCode.NOT_LOGGED_IN})
    @GetMapping("/similar")
    public ApiResponse<List<SimilarPostVo>> similar(
            @RequestParam String title,
            Authentication authentication) {
        Long userId = CurrentUser.requireId(authentication);
        List<SimilarPostResult> results = forumQueryService.findSimilarPosts(title, userId);
        return ApiResponse.ok(results.stream().map(SimilarPostVo::from).toList());
    }

    @Operation(summary = "帖子详情（公开）：返回服务端渲染好的 contentHtml；登录请求附带 likedByMe / favoritedByMe，匿名时恒 false；阅读数按「每帖每账号每日一次」去重累加（CR-074）")
    @ErrorCodes({ResultCode.NOT_FOUND})
    @PublicEndpoint
    @GetMapping("/{id}")
    public ApiResponse<PostDetailVo> detail(@PathVariable("id") Long id,
                                            Authentication authentication,
                                            jakarta.servlet.http.HttpServletRequest request) {
        Long viewerId = CurrentUser.optionalId(authentication);
        viewCounter.record(id, viewerId, IpUtil.clientIp(request));
        return ApiResponse.ok(PostDetailVo.from(forumQueryService.postDetail(id, viewerId)));
    }

    @Operation(summary = "「关注」Feed（需登录）：本人关注作者的时间序帖子；未关注任何人时为空页（CR-074）")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ResultCode.NOT_LOGGED_IN})
    @GetMapping("/following")
    public ApiResponse<PageVo<PostSummaryVo>> following(@RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "20") int size,
                                                        Authentication authentication) {
        long userId = CurrentUser.requireId(authentication);
        PageResult<PostSummary> result = forumQueryService.listFollowingPosts(userId, page, size);
        return ApiResponse.ok(new PageVo<>(result.items().stream().map(PostSummaryVo::from).toList(),
                result.total(), result.page(), result.size()));
    }

    @Operation(summary = "发帖（需登录）：只返回 id，前端据此跳转详情")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ResultCode.NOT_LOGGED_IN, ResultCode.NOT_FOUND, ResultCode.USER_BANNED,
            ResultCode.ACCOUNT_DEACTIVATED, ResultCode.MODERATION_BLOCKED, ResultCode.MODERATION_UNAVAILABLE})
    @PostMapping
    public ApiResponse<PublishedPost> publish(@Valid @RequestBody PublishPostCommand command,
                                              Authentication authentication) {
        long userId = CurrentUser.requireId(authentication);
        return ApiResponse.ok(postApplicationService.publish(userId, command));
    }

    @Operation(summary = "采纳最佳答案（需登录，仅提问者）：技术问答帖的指定回复标记为最佳，可更换；body 为 {replyId}")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ResultCode.NOT_LOGGED_IN, ResultCode.FORBIDDEN, ResultCode.NOT_FOUND,
            ResultCode.NOT_A_QUESTION, ResultCode.CANNOT_ACCEPT_OWN_REPLY})
    @PostMapping("/{id}/accept")
    public ApiResponse<Void> accept(@PathVariable("id") Long id,
                                    @Valid @RequestBody AcceptReplyCommand command,
                                    Authentication authentication) {
        long userId = CurrentUser.requireId(authentication);
        postApplicationService.acceptReply(userId, id, command.replyId());
        return ApiResponse.ok();
    }

    @Operation(summary = "删除自己的帖子（需登录，仅作者）：写行级墓碑，前台所有位置随之不可见；不级联删楼层与互动行")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ResultCode.NOT_LOGGED_IN, ResultCode.FORBIDDEN, ResultCode.NOT_FOUND})
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") Long id, Authentication authentication) {
        long userId = CurrentUser.requireId(authentication);
        postApplicationService.deletePost(userId, id);
        return ApiResponse.ok();
    }

    @Operation(summary = "点赞 / 取消点赞帖子（需登录，toggle）：active 为操作后状态，count 为最新点赞数")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ResultCode.NOT_LOGGED_IN, ResultCode.NOT_FOUND})
    @PostMapping("/{id}/like")
    public ApiResponse<InteractionResult> like(@PathVariable("id") Long id, Authentication authentication) {
        long userId = CurrentUser.requireId(authentication);
        return ApiResponse.ok(interactionApplicationService.togglePostLike(userId, id));
    }

    @Operation(summary = "收藏 / 取消收藏帖子（需登录，toggle）：active 为操作后状态，count 为最新收藏数")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ResultCode.NOT_LOGGED_IN, ResultCode.NOT_FOUND})
    @PostMapping("/{id}/favorite")
    public ApiResponse<InteractionResult> favorite(@PathVariable("id") Long id, Authentication authentication) {
        long userId = CurrentUser.requireId(authentication);
        return ApiResponse.ok(interactionApplicationService.togglePostFavorite(userId, id));
    }
}
