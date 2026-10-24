package com.campuslink.module.forum.web;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ApiResponse;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.forum.application.AdminTagApplicationService;
import com.campuslink.module.forum.web.vo.AdminTagsVo;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 标签管理两个端点（CR-132 / F-ADMIN-004 标签侧）的授权三态：匿名 401、普通用户 403、SUPERADMIN 放行。
 *
 * <p>ROLE_USER 那两例是本端点真正的防线：路径级框架只区分「登录 / 未登录」，
 * 「仅 SUPERADMIN」只存在于 {@code CurrentUser.requireRole}。
 */
class AdminTagControllerAuthTest {

    private static final long TAG_ID = 8L;

    private final AdminTagApplicationService service = mock(AdminTagApplicationService.class);
    private final AdminTagController controller = new AdminTagController(service);

    @Test
    @DisplayName("匿名查标签列表 → 4001，且不触达用例")
    void anonymousListIsUnauthorized() {
        assertThatThrownBy(() -> controller.tags(null, "use_count", 1, 20, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));
        verify(service, never()).page(any(), anyString(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("匿名删标签 → 4001，且不触达用例")
    void anonymousDeleteIsUnauthorized() {
        assertThatThrownBy(() -> controller.deleteTag(TAG_ID, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));
        verify(service, never()).delete(anyLong(), anyLong());
    }

    @Test
    @DisplayName("普通用户删标签 → 4002，且不触达用例")
    void ordinaryUserDeleteIsForbidden() {
        assertThatThrownBy(() -> controller.deleteTag(TAG_ID, auth("ROLE_USER")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.FORBIDDEN));
        verify(service, never()).delete(anyLong(), anyLong());
    }

    @Test
    @DisplayName("普通用户查列表 → 4002，且不触达用例")
    void ordinaryUserListIsForbidden() {
        assertThatThrownBy(() -> controller.tags(null, "use_count", 1, 20, auth("ROLE_USER")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.FORBIDDEN));
        verify(service, never()).page(any(), anyString(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("SUPERADMIN 查列表 → 200，字段按条目映射")
    void superadminListMapsVo() {
        when(service.page("ja", "name", 2, 10)).thenReturn(new AdminTagApplicationService.AdminTags(
                List.of(new AdminTagApplicationService.AdminTagView(TAG_ID, "java", 7,
                        Instant.parse("2026-10-20T10:00:00Z"))),
                1L, 2, 10));

        ApiResponse<AdminTagsVo> response = controller.tags("ja", "name", 2, 10, auth("ROLE_SUPERADMIN"));

        assertThat(response.code()).isZero();
        assertThat(response.data().total()).isEqualTo(1);
        assertThat(response.data().page()).isEqualTo(2);
        assertThat(response.data().items().get(0).name()).isEqualTo("java");
        assertThat(response.data().items().get(0).useCount()).isEqualTo(7);
    }

    @Test
    @DisplayName("SUPERADMIN 删标签 → 操作人取自令牌并调用用例")
    void superadminDeleteUsesPrincipal() {
        ApiResponse<Void> response = controller.deleteTag(TAG_ID, auth("ROLE_SUPERADMIN"));

        assertThat(response.code()).isZero();
        verify(service).delete(1L, TAG_ID);
    }

    private static Authentication auth(String authority) {
        return new UsernamePasswordAuthenticationToken(1L, "",
                List.of(new SimpleGrantedAuthority(authority)));
    }
}
