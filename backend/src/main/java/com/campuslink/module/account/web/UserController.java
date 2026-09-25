package com.campuslink.module.account.web;

import com.campuslink.common.result.ApiResponse;
import com.campuslink.common.result.ErrorCodes;
import com.campuslink.common.result.ResultCode;
import com.campuslink.common.web.ApiDocs;
import com.campuslink.common.web.CurrentUser;
import com.campuslink.common.web.IpUtil;
import com.campuslink.common.web.PublicEndpoint;
import com.campuslink.module.account.application.AccountApplicationService;
import com.campuslink.module.account.application.ProfileApplicationService;
import com.campuslink.module.account.application.UserProfileApplicationService;
import com.campuslink.module.account.application.cmd.AccountCommands.UpdateProfileCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户接口（web 层）：只经 application 服务取数。
 * 不注入 {@code domain.gateway} 端口——R-1 裁决（CR-028）与 {@code ArchitectureGuardTest} 的执行结果。
 *
 * <p>本类同时持有两个方向的端点：{@code /me} 两个是**本人视角**（需登录，id 只从令牌取），
 * {@code /{id}} 是**路人视角**（公开）。两者不共用 VO 也不共用 application 方法，
 * 因为可见字段集不同（{@code UserVo} 带 role / status / verified，{@code UserProfileVo} 一律不带）。
 */
@Tag(name = "user", description = "用户")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final AccountApplicationService accountApplicationService;
    private final ProfileApplicationService profileApplicationService;
    private final UserProfileApplicationService userProfileApplicationService;

    @Operation(summary = "我的主页（F-ACC-002 最小版；他人主页见 GET /users/{id}）")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ResultCode.NOT_LOGGED_IN, ResultCode.USER_NOT_FOUND})
    @GetMapping("/me")
    public ApiResponse<UserVo> me(Authentication authentication) {
        long userId = CurrentUser.requireId(authentication);
        return ApiResponse.ok(UserVo.from(accountApplicationService.accountOf(userId)));
    }

    /**
     * 他人主页的公开资料（F-ACC-002 本体的最小版）：展示四列 + 三个计数，匿名也可读（公开端点）。
     *
     * <p>路径变量与 {@code GET /me} 共存：Spring 的 {@code PathPattern} 比较器把字面量段排在变量段之前，
     * {@code /users/me} 永远先命中 {@link #me}。这不需要测试兜住——但反过来不成立：
     * 把 {@code /me} 改成另一种变量形式（如 {@code /{name}}）就会真的抢掉它。
     *
     * <p>不存在与已注销统一 2007 / 404（不对外区分，不给按 id 探测账号状态的口子）；
     * 被封禁的账号仍可读——封禁限的是发言与登录，全站列表 / 搜索从不按作者状态过滤，
     * 主页单独消失会让列表里那个作者名点不开。
     *
     * <p>⚠️ 本端点是匿名的，故**不产出** {@code following}（“我有没有关注他”）：那一位需要 viewer，
     * 已由登录态的 {@code GET /users/{targetId}/follow-state} 供给（前端在已登录时另调）。
     *
     * <p>匿名可读 ⇒ id 又是自增主键，故按 IP 限流防批量枚举（30 次/分钟，判据在 application）；
     * IP 由 {@link IpUtil#clientIp} 提取（反代场景优先 X-Forwarded-For 首段），与学籍核验限流、
     * 阅读数去重同一把尺。登录用户同受此限——限的是 IP 不是账号，同一宿舍 NAT 出口共享配额
     * 是已知取舍：阈值按“正常人不会在 1 分钟看 30 个主页”留了余量。
     */
    @Operation(summary = "用户公开资料（公开）：昵称 / 专业 / 签名 / 加入时间 + 帖子数 / 粉丝数 / 关注数；用户不存在或已注销 404 / 2007；按 IP 限流 30 次/分钟")
    @PublicEndpoint
    @ErrorCodes({ResultCode.USER_NOT_FOUND, ResultCode.PROFILE_VIEW_RATE_LIMITED})
    @GetMapping("/{id}")
    public ApiResponse<UserProfileVo> profile(@PathVariable("id") long id, HttpServletRequest request) {
        return ApiResponse.ok(UserProfileVo.from(
                userProfileApplicationService.publicProfileOf(id, IpUtil.clientIp(request))));
    }

    /**
     * 资料编辑（F-ACC-007a）：PUT 全量语义，只接受昵称 / 专业 / 签名三个字段。
     *
     * <p><b>URL 与请求体都不带身份参数</b>——作者 id 唯一来源是令牌。这不是省事：
     * "仅本人"这条线没有机器强制（问题清单 E-011 同类缺口），所以越权面靠**接口形状**保证为零。
     *
     * <p>{@code @ErrorCodes} 里**不写** {@code INVALID_PARAM}：{@code OpenApiErrorResponseCustomizer}
     * 自动补通用 400，显式声明会让契约里出现"参数错误（1001）；参数错误（1001）"
     * （[CR-049] 的 v1.8 教训，本轮重抓时实测复现过一次并删掉）。
     */
    @Operation(summary = "修改我的资料（需登录）：昵称 / 专业 / 签名；字段传 null 即不改、传空串即清空（昵称除外）；全量回显 UserVo")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ResultCode.NOT_LOGGED_IN, ResultCode.USER_NOT_FOUND,
            ResultCode.USER_BANNED, ResultCode.PROFILE_UPDATE_TOO_FREQUENT})
    @PutMapping("/me")
    public ApiResponse<UserVo> updateProfile(@Valid @RequestBody UpdateProfileCommand command,
                                             Authentication authentication) {
        long userId = CurrentUser.requireId(authentication);
        return ApiResponse.ok(UserVo.from(profileApplicationService.updateProfile(userId, command)));
    }

    /**
     * 注销账号（F-ACC-003 / CR-082）：进入 7 天冷静期——期内登录即视为撤销（恢复 ACTIVE），
     * 期满由匿名化定时任务清除实名信息。二次确认由前端弹窗承担，本端点幂等可重入。
     */
    @Operation(summary = "注销账号（需登录）：进入 7 天冷静期；期内登录即撤销注销，期满匿名化")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ResultCode.NOT_LOGGED_IN, ResultCode.USER_NOT_FOUND})
    @PostMapping("/me/deactivate")
    public ApiResponse<Void> deactivate(Authentication authentication) {
        accountApplicationService.deactivate(CurrentUser.requireId(authentication));
        return ApiResponse.ok();
    }
}
