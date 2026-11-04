package com.campuslink.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * JWT 鉴权过滤器：解析 Bearer token 并注入 SecurityContext（principal = 用户 ID）。
 * 无效 / 缺失 token 视为未登录继续走匿名链路；权限收紧在 Sprint 2+ 的 FilterChain 中处理。
 *
 * <p>角色层级（CR-196）：SUPERADMIN 同时持有 {@code ROLE_OPS}——超管可执行运营的全部动作，
 * 但 OPS 不因此获得处置 / 封禁 / 名册 / 标签 / 看板权限。权限只来自 token 中的角色声明，
 * 因此角色变更须重新登录才生效（降权即时失效需 token 吊销，另开 CR）。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = jwtService.parse(header.substring(7));
                Long userId = Long.valueOf(claims.getSubject());
                String role = claims.get("role", String.class);
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
}
