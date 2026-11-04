package com.campuslink.module.forum.web;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ApiResponse;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.forum.application.ReportApplicationService;
import com.campuslink.module.forum.domain.gateway.PageResult;
import com.campuslink.module.forum.domain.model.Report;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 举报工单端点（F-ADMIN-003、CR-196）的授权：匿名 401、普通用户 403、OPS / SUPERADMIN 放行。
 *
 * <p>工单处理是 OPS 的正当职责（PRD F-ADMIN-003「运营可执行处置 / 驳回」）；
 * 处置台本身仍仅 SUPERADMIN，两条线由 {@link AdminModerationControllerAuthTest} 分开守护。
 */
class AdminReportControllerAuthTest {

    private final ReportApplicationService service = mock(ReportApplicationService.class);
    private final AdminReportController controller = new AdminReportController(service);

    @Test
    @DisplayName("匿名查工单 → 4001，且不触达用例")
    void anonymousPageIsUnauthorized() {
        assertThatThrownBy(() -> controller.page(null, 1, 20, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));
        verify(service, never()).adminPage(anyString(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("普通用户查工单 → 4002，且不触达用例")
    void ordinaryUserPageIsForbidden() {
        assertThatThrownBy(() -> controller.page(null, 1, 20, auth("ROLE_USER")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.FORBIDDEN));
        verify(service, never()).adminPage(anyString(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("OPS 查工单 → 200，条目按出参映射")
    void opsPageMapsItems() {
        when(service.adminPage("PENDING", 1, 20)).thenReturn(new PageResult<>(
                List.of(new Report(3L, 42L, Report.TARGET_POST, 9L, "SPAM", "广告",
                        "PENDING", null, null, Instant.parse("2026-11-04T08:00:00Z"), null)),
                1L, 1, 20));

        ApiResponse<PageVo<AdminReportController.AdminReportVo>> response =
                controller.page("PENDING", 1, 20, auth("ROLE_OPS"));

        assertThat(response.code()).isZero();
        assertThat(response.data().total()).isEqualTo(1);
        assertThat(response.data().list().get(0).reason()).isEqualTo("SPAM");
    }

    @Test
    @DisplayName("匿名处理工单 → 4001，且不触达用例")
    void anonymousCloseIsUnauthorized() {
        assertThatThrownBy(() -> controller.close(1L, new AdminReportController.CloseCommand("DISMISS", "误报"), null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));
        verify(service, never()).close(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("OPS 处理工单 → 200，操作人取自 principal（工单闭环属于运营职责）")
    void opsClosePassesThrough() {
        assertThat(controller.close(1L, new AdminReportController.CloseCommand("DISMISS", "误报"), auth("ROLE_OPS"))
                .code()).isZero();

        verify(service).close(8L, 1L, "DISMISS", "误报");
    }

    private static Authentication auth(String authority) {
        return new UsernamePasswordAuthenticationToken(8L, "",
                List.of(new SimpleGrantedAuthority(authority)));
    }
}
