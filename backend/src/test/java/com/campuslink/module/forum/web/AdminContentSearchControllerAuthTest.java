package com.campuslink.module.forum.web;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ApiResponse;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.forum.application.AdminContentSearchApplicationService;
import com.campuslink.module.forum.application.AdminContentSearchApplicationService.AdminContentItem;
import com.campuslink.module.forum.application.AdminContentSearchApplicationService.AdminContentPage;
import com.campuslink.module.forum.web.vo.AdminContentSearchVo;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 后台内容搜索端点（CR-141 / F-ADMIN-002）的授权三态：匿名 401、普通用户 403、SUPERADMIN 放行。
 *
 * <p>ROLE_USER 那一例是本端点真正的防线：路径级框架只区分「登录 / 未登录」，
 * 「仅 SUPERADMIN」只存在于 {@code CurrentUser.requireRole}。
 */
class AdminContentSearchControllerAuthTest {

    private final AdminContentSearchApplicationService service = mock(AdminContentSearchApplicationService.class);
    private final AdminContentSearchController controller = new AdminContentSearchController(service);

    @Test
    @DisplayName("匿名搜索 → 4001，且不触达用例")
    void anonymousSearchIsUnauthorized() {
        assertThatThrownBy(() -> controller.search("post", null, null, null, null, 1, 20, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));
        verify(service, never()).search(any(), any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("普通用户搜索 → 4002，且不触达用例")
    void ordinaryUserSearchIsForbidden() {
        assertThatThrownBy(() -> controller.search("post", null, null, null, null, 1, 20, user("ROLE_USER")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.FORBIDDEN));
        verify(service, never()).search(any(), any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("SUPERADMIN 搜索 → 200，条目按出参映射")
    void superadminSearchMapsItems() {
        when(service.search("post", "Redis", null, null, 7, 1, 20)).thenReturn(new AdminContentPage(
                "post", List.of(new AdminContentItem(7L, "post", "标题", "摘要", "技术问答",
                        42L, "作者甲", "PUBLISHED", Instant.parse("2026-10-25T08:00:00Z"))), 1, 1, 20));

        ApiResponse<AdminContentSearchVo> response = controller.search(
                "post", "Redis", null, null, 7, 1, 20, user("ROLE_SUPERADMIN"));

        assertThat(response.code()).isZero();
        AdminContentSearchVo vo = response.data();
        assertThat(vo.kind()).isEqualTo("post");
        assertThat(vo.total()).isEqualTo(1);
        assertThat(vo.items().get(0).id()).isEqualTo(7L);
        assertThat(vo.items().get(0).summary()).isEqualTo("摘要");
        assertThat(vo.items().get(0).authorNickname()).isEqualTo("作者甲");
    }

    private static Authentication user(String authority) {
        return new UsernamePasswordAuthenticationToken(9L, "",
                List.of(new SimpleGrantedAuthority(authority)));
    }
}
