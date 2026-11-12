package com.campuslink.security;

import com.campuslink.common.security.TokenRevocationGateway;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JWT 鉴权过滤器：解析 Bearer token 并注入 SecurityContext（principal = 用户 ID）。
 * 无效 / 缺失 token 视为未登录继续走匿名链路；权限收紧在 Sprint 2+ 的 FilterChain 中处理。
 *
 * <p>角色层级（CR-196）：SUPERADMIN 同时持有 {@code ROLE_OPS}——超管可执行运营的全部动作，
 * 但 OPS 不因此获得处置 / 封禁 / 名册 / 标签 / 看板权限。权限只来自 token 中的角色声明，
 * 因此角色变更须重新登录才生效。
 *
 * <p>吊销（CR-208）：解析成功后按用户级水位判断 token 是否已作废——注销 / 封禁 / 授予 OPS
 * 会写入水位，旧 token 下一请求即按未登录处理。Redis 不可用时 fail-open（记 WARN），
 * 避免缓存故障把全体登录态一并打掉；该取向与「吊销是增强项、不是唯一防线」一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final TokenRevocationGateway tokenRevocationGateway;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = jwtService.parse(header.substring(7));
                Long userId = Long.valueOf(claims.getSubject());
                String role = claims.get("role", String.class);
                Instant issuedAt = claims.getIssuedAt() == null ? null : claims.getIssuedAt().toInstant();
                if (issuedAt != null && isRevoked(userId, issuedAt)) {
                    // 已吊销：按未登录继续，受保护端点由 EndpointAuthorizationManager 统一出 401
                    chain.doFilter(request, response);
                    return;
                }
                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                if ("SUPERADMIN".equals(role)) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_OPS"));
                }
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userId, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | NumberFormatException ignored) {
                // 无效 token 视为未登录
            }
        }
        chain.doFilter(request, response);
    }

    private boolean isRevoked(Long userId, Instant issuedAt) {
        try {
            return tokenRevocationGateway.isRevoked(userId, issuedAt);
        } catch (RuntimeException e) {
            log.warn("token revocation check failed; treating token as active: {}", e.toString());
            return false;
        }
    }
}
