package com.campuslink.module.forum.application;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.forum.application.AdminAuditApplicationService.AuditItem;
import com.campuslink.module.forum.domain.gateway.AdminAuditGateway;
import com.campuslink.module.forum.domain.gateway.AdminAuditGateway.AuditRow;
import com.campuslink.module.forum.domain.gateway.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 操作审计只读用例（CR-215）：参数归一、时间窗校验、分页归一与操作者展示名兜底。 */
@ExtendWith(MockitoExtension.class)
class AdminAuditApplicationServiceTest {

    private static final long AUDIT_ID = 11L;
    private static final long ACTOR_ID = 1L;
    private static final Instant CREATED_AT = Instant.parse("2026-11-10T08:00:00Z");

    @Mock
    private AdminAuditGateway gateway;

    @InjectMocks
    private AdminAuditApplicationService service;

    @Test
    @DisplayName("缺省筛选：action / actorId / days 均传 null，page 0 → 1、size 999 → 100，条目原样映射")
    void defaultsNormalizeAndMapRows() {
        when(gateway.page(isNull(), isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(new PageResult<>(List.of(row(ACTOR_ID, "管理员甲", "USER_BAN")), 1, 1, 100));

        PageResult<AuditItem> result = service.page(null, null, null, 0, 999);

        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(100);
        assertThat(result.total()).isEqualTo(1);
        AuditItem item = result.items().get(0);
        assertThat(item.id()).isEqualTo(AUDIT_ID);
        assertThat(item.actorId()).isEqualTo(ACTOR_ID);
        assertThat(item.actorNickname()).isEqualTo("管理员甲");
        assertThat(item.action()).isEqualTo("USER_BAN");
        assertThat(item.targetType()).isEqualTo("users");
        assertThat(item.targetId()).isEqualTo(42L);
        assertThat(item.detail()).isEqualTo("reason=灌水");
        assertThat(item.createdAt()).isEqualTo(CREATED_AT);
        verify(gateway).page(null, null, null, 1, 100);
    }

    @Test
    @DisplayName("action 去首尾空白后精确下传；纯空白视为不筛选")
    void actionIsTrimmedAndBlankFallsBackToNull() {
        when(gateway.page(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageResult<>(List.of(), 0, 1, 20));

        service.page("  USER_BAN  ", 7L, 30, 1, 20);
        verify(gateway).page("USER_BAN", 7L, 30, 1, 20);

        service.page("   ", null, null, 1, 20);
        verify(gateway).page(null, null, null, 1, 20);
    }

    @Test
    @DisplayName("days 非 7 / 30 / 90 → 1001，且不触达网关")
    void invalidDaysIsRejected() {
        assertThatThrownBy(() -> service.page(null, null, 8, 1, 20))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));
        verify(gateway, never()).page(any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("action 超过 64 字（列宽）→ 1001，且不触达网关")
    void tooLongActionIsRejected() {
        assertThatThrownBy(() -> service.page("A".repeat(65), null, null, 1, 20))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));
        verify(gateway, never()).page(any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("操作者展示名兜底：actorId 为空 → 系统；昵称缺失 → 已注销用户")
    void actorNicknameFallsBack() {
        when(gateway.page(isNull(), isNull(), isNull(), anyInt(), anyInt())).thenReturn(
                new PageResult<>(List.of(row(null, null, "ACCOUNT_ANONYMIZE"),
                        row(ACTOR_ID, "  ", "ROSTER_IMPORT")), 2, 1, 20));

        List<AuditItem> items = service.page(null, null, null, 1, 20).items();

        assertThat(items.get(0).actorNickname()).isEqualTo("系统");
        assertThat(items.get(1).actorNickname()).isEqualTo("已注销用户");
        assertThat(items.get(0).actorId()).isNull();
        assertThat(items.get(1).actorId()).isEqualTo(ACTOR_ID);
    }

    private static AuditRow row(Long actorId, String actorNickname, String action) {
        return new AuditRow(AUDIT_ID, actorId, actorNickname, action, "users", 42L, "reason=灌水", CREATED_AT);
    }
}
