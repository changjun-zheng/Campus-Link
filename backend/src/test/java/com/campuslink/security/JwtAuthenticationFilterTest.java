package com.campuslink.security;

import com.campuslink.config.AppProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JWT 角色映射（CR-196）：SUPERADMIN 叠加 ROLE_OPS，OPS / USER 保持单权限。
 *
 * <p>这条层级是 OPS 端点授权的运行时基础——OPS 端点在 Controller 里只判 {@code ROLE_OPS}，
 * 超管能否继续调用这些端点完全取决于本过滤器是否补发第二个 authority。
 */
class JwtAuthenticationFilterTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    private final JwtService jwtService = jwtService();
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("SUPERADMIN token → ROLE_SUPERADMIN + ROLE_OPS（超管可执行运营动作）")
    void superadminCarriesOpsAuthority() throws Exception {
        Authentication authentication = authenticate("SUPERADMIN");

        assertThat(authentication.getPrincipal()).isEqualTo(7L);
        assertThat(authorities(authentication))
                .containsExactlyInAnyOrder("ROLE_SUPERADMIN", "ROLE_OPS");
    }

    @Test
    @DisplayName("OPS token → 仅 ROLE_OPS（不得继承处置 / 封禁等超管权限）")
    void opsCarriesOnlyOpsAuthority() throws Exception {
        Authentication authentication = authenticate("OPS");

        assertThat(authorities(authentication)).containsExactly("ROLE_OPS");
    }

    @Test
    @DisplayName("普通用户 token → 仅 ROLE_USER")
    void studentCarriesOnlyUserAuthority() throws Exception {
        Authentication authentication = authenticate("USER");

        assertThat(authorities(authentication)).containsExactly("ROLE_USER");
    }

    private Authentication authenticate(String role) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + jwtService.issue(7L, role));
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private static List<String> authorities(Authentication authentication) {
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    private static JwtService jwtService() {
        AppProperties props = new AppProperties();
        props.getJwt().setSecret(SECRET);
        return new JwtService(props);
    }
}
