package com.campuslink.module.forum.web;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ApiResponse;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.forum.application.AdminAuditApplicationService;
import com.campuslink.module.forum.application.AdminAuditApplicationService.AuditItem;
import com.campuslink.module.forum.domain.gateway.PageResult;
import com.campuslink.module.forum.web.vo.AdminAuditVo;
import com.campuslink.module.forum.web.vo.PageVo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 操作审计端点（CR-215）的授权三态：匿名 401、普通用户 / OPS 403、SUPERADMIN 放行并映射。
 *
 * <p>OPS 那一例是本端点真正的防线：路径级框架只区分「登录 / 未登录」，
 * 「仅 SUPERADMIN」只存在于 {@code CurrentUser.requireRole}——摘掉它后匿名例仍绿、OPS 例会红。
 */
class AdminAuditControllerAuthTest {

    private final AdminAuditApplicationService service = mock(AdminAuditApplicationService.class);
    private final AdminAuditController controller = new AdminAuditController(service);

    @Test
    @DisplayName("匿名查询审计 → 4001，且不触达用例")
    void anonymousAuditIsUnauthorized() {
        assertThatThrownBy(() -> controller.page(null, null, null, 1, 20, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));
        verify(service, never()).page(any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("普通用户查询审计 → 4002，且不触达用例")
    void ordinaryUserAuditIsForbidden() {
        assertThatThrownBy(() -> controller.page(null, null, null, 1, 20, user("ROLE_USER")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.FORBIDDEN));
        verify(service, never()).page(any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("OPS 查询审计 → 4002（操作审计仍仅 SUPERADMIN）")
    void opsAuditIsForbidden() {
        assertThatThrownBy(() -> controller.page(null, null, null, 1, 20, user("ROLE_OPS")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.FORBIDDEN));
        verify(service, never()).page(any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("SUPERADMIN 查询 → 200，筛选与分页原样下传，条目映射为 VO")
    void superadminAuditMapsItems() {
        Instant createdAt = Instant.parse("2026-11-10T08:00:00Z");
        when(service.page(isNull(), isNull(), isNull(), anyInt(), anyInt())).thenReturn(
                new PageResult<>(List.of(new AuditItem(11L, 7L, "管理员甲", "USER_BAN", "users", 42L,
                        "reason=灌水", createdAt)), 1, 1, 20));

        ApiResponse<PageVo<AdminAuditVo>> response =
                controller.page(null, null, null, 1, 20, user("ROLE_SUPERADMIN"));

        assertThat(response.code()).isZero();
        PageVo<AdminAuditVo> page = response.data();
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(20);
        AdminAuditVo vo = page.list().get(0);
        assertThat(vo.id()).isEqualTo(11L);
        assertThat(vo.actorId()).isEqualTo(7L);
        assertThat(vo.actorNickname()).isEqualTo("管理员甲");
        assertThat(vo.action()).isEqualTo("USER_BAN");
        assertThat(vo.targetType()).isEqualTo("users");
        assertThat(vo.targetId()).isEqualTo(42L);
        assertThat(vo.detail()).isEqualTo("reason=灌水");
        assertThat(vo.createdAt()).isEqualTo(createdAt);
    }

    private static Authentication user(String authority) {
        return new UsernamePasswordAuthenticationToken(9L, "",
                List.of(new SimpleGrantedAuthority(authority)));
    }
}
