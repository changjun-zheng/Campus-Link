package com.campuslink.config;

import com.campuslink.security.ApiErrorSecurityHandler;
import com.campuslink.security.EndpointAuthorizationManager;
import com.campuslink.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.Header;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final EndpointAuthorizationManager endpointAuthorizationManager;
    private final ApiErrorSecurityHandler apiErrorSecurityHandler;
    private final AppProperties appProperties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 路径级鉴权（CR-031）：按端点注解（@PublicEndpoint / @SecurityRequirement）裁决，
                // module 端点未声明即 fail-closed——不再有 permitAll 兜底
                .authorizeHttpRequests(auth -> auth.anyRequest().access(endpointAuthorizationManager))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(apiErrorSecurityHandler)
                        .accessDeniedHandler(apiErrorSecurityHandler))
                // 安全响应头（E-012 ③ / CR-118）：API 响应不承载 HTML，CSP 取最严的 default-src 'none'；
                // SPA 页面自身的 CSP 在 frontend/index.html 的 meta 中（同源静态托管与 Vite 开发均生效）
                .headers(headers -> headers.addHeaderWriter(new StaticHeadersWriter(List.of(
                        new Header("Referrer-Policy", "strict-origin-when-cross-origin"),
                        new Header("Permissions-Policy", "camera=(), microphone=(), geolocation=()"),
                        new Header("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'")))))
                // 无状态 API 不存"重放请求"：否则 401 响应会因默认 request cache 创建 session
                .requestCache(AbstractHttpConfigurer::disable)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(appProperties.getCors().getAllowedOrigins().split(","))
                .map(String::trim).toList());
        config.setAllowedMethods(List.of("*"));
        config.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
