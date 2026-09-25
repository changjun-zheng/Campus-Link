package com.campuslink.common.web;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import org.springframework.security.core.Authentication;

/**
 * 受保护端点的统一鉴权入口（N-4 闭环，CR-028）。
 *
 * <p>背景：{@code SecurityConfig} 曾长期 {@code anyRequest().permitAll()}，登录态只能由 Controller 主动校验，
 * 而 {@code @SecurityRequirement} 只是契约声明——"新端点漏写校验"会静默变成公开接口。
 * 路径级拦截已由 [CR-031] 的 {@code security/EndpointAuthorizationManager} 落地：匿名调用受保护端点在
 * **进入业务代码前**即被拒（过滤器链直接产出 401 / 4001）。本类仍是**业务侧的统一入口**，
 * 负责"已登录者"的角色与资源级授权（{@code requireRole} → 403 / 4002），
 * 并由 {@code ArchitectureGuardTest} 规定**凡声明 {@code @SecurityRequirement} 的方法必须真的调用本类方法**。
 *
 * <p>⚠️ 框架层只区分"登录 / 未登录"，**不**做角色判定；"仅作者可删"这类规则必须由本类或领域逻辑承担。
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** 取当前登录账号 id；未登录（无 Authentication 或 principal 不是账号 id）抛 401。 */
    public static long requireId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new ApiException(ResultCode.NOT_LOGGED_IN);
        }
        return userId;
    }

    /**
     * 取当前登录账号 id 且必须持有指定权限（Spring authority 全名，如 {@code ROLE_SUPERADMIN}）。
     * 未登录 401、已登录但权限不足 403——两者必须分开（技术方案 §5）。
     */
    public static long requireRole(Authentication authentication, String authority) {
        long userId = requireId(authentication);
        boolean granted = authentication.getAuthorities().stream()
                .anyMatch(a -> authority.equals(a.getAuthority()));
        if (!granted) {
            throw new ApiException(ResultCode.FORBIDDEN);
        }
        return userId;
    }

    /**
     * 公开端点的**可选**身份：已登录返回账号 id，匿名（无 Authentication 或 principal 不是账号 id）返回 {@code null} 而不抛 401。
     *
     * <p>存在的理由是"公开可读、登录附加回显"这一类端点（详情的 likedByMe、楼层的点赞态、列表的点赞态）
     * 都需要它。此前这份判定在 {@code PostController} 与 {@code ReplyController} 各抄了一份私有
     * {@code viewerIdOf}，[CR-080] 把第三、四份要加出来时收敛到此处——"匿名怎么算"只能有一处答案。
     *
     * <p>⚠️ 本方法**不是授权**入口：它不拦任何人，公开端点仍由 {@code @PublicEndpoint} 放行、
     * 受保护端点必须用 {@link #requireId}。拿它替代 {@code requireId} 等于把受保护端点做成匿名可读。
     */
    public static Long optionalId(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof Long userId ? userId : null;
    }
}
