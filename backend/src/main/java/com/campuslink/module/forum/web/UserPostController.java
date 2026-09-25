package com.campuslink.module.forum.web;

import com.campuslink.common.result.ApiResponse;
import com.campuslink.common.result.ErrorCodes;
import com.campuslink.common.result.ResultCode;
import com.campuslink.common.web.CurrentUser;
import com.campuslink.common.web.PublicEndpoint;
import com.campuslink.module.forum.application.ForumQueryApplicationService;
import com.campuslink.module.forum.application.cmd.ForumResults.PostSummary;
import com.campuslink.module.forum.domain.gateway.PageResult;
import com.campuslink.module.forum.web.vo.PageVo;
import com.campuslink.module.forum.web.vo.PostSummaryVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 某用户的公开帖子（他人主页 {@code /u/:id} 的时间线，F-ACC-002 本体最小版）。
 *
 * <p>为什么不挂进 account 的 {@code UserController}（那条 URL 命名空间确实在 account 手里）：
 * 帖子是 forum 的数据，出参 {@link PostSummaryVo} 与分页壳 {@link PageVo} 都在 forum.web —— 跨上下文只允许
 * 依赖对方 **application** 包（守护测试 G4），account.web 拿不到这两个 web 类型，硬要拿就得在 account 侧
 * 复制一份 12 字段的帖子摘要 VO，而两份同形 VO 迟早漂移（首页与主页的同一条帖子长得不一样）。
 * 这与 {@link MyReplyController} 是同一族做法：功能归属 account（"我的回帖"是 F-ACC-007c），
 * 端点落在数据所属的 forum.web。tag 仍复用 {@code post}，契约里 tag 数不因新开映射类而增加。
 *
 * <p>URL 上的 {@code {id}} 是**用户 id 而不是帖子 id**（类级前缀是 {@code /api/v1/users}），
 * 与 {@code PostController} 的 {@code /api/v1/posts/{id}} 不同命名空间，不构成映射冲突。
 *
 * <p>公开端点（{@link PublicEndpoint}）：主页要能被匿名访客打开——"关注了人却没地方看人"是这一条的立论。
 * 用户不存在 / 已注销由应用层统一 2007 / 404，控制器不做二次判定。
 */
@Tag(name = "post", description = "帖子")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserPostController {

    private final ForumQueryApplicationService forumQueryService;

    @Operation(summary = "某用户的公开帖子（公开）：时间倒序分页，口径同全站列表（不含已删除与已下架）；用户不存在或已注销 404 / 2007")
    @PublicEndpoint
    @ErrorCodes({ResultCode.USER_NOT_FOUND})
    @GetMapping("/{id}/posts")
    public ApiResponse<PageVo<PostSummaryVo>> userPosts(@PathVariable("id") long id,
                                                        @RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "20") int size,
                                                        Authentication authentication) {
        PageResult<PostSummary> result = forumQueryService.listVisiblePostsByAuthor(id, page, size,
                CurrentUser.optionalId(authentication));
        return ApiResponse.ok(new PageVo<>(result.items().stream().map(PostSummaryVo::from).toList(),
                result.total(), result.page(), result.size()));
    }
}
