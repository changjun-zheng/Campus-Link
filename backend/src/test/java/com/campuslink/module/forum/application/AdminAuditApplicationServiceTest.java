package com.campuslink.module.forum.application;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.forum.application.AdminAuditApplicationService.AuditExport;
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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 操作审计只读用例（CR-215 / CR-216）：参数归一、时间窗校验、分页归一、展示名兜底与 CSV 导出。 */
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
    @DisplayName("缺省筛选：全部参数传 null，page 0 → 1、size 999 → 100，条目原样映射")
    void defaultsNormalizeAndMapRows() {
        when(gateway.page(isNull(), isNull(), isNull(), isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(new PageResult<>(List.of(row(ACTOR_ID, "管理员甲", "USER_BAN")), 1, 1, 100));

        PageResult<AuditItem> result = service.page(null, null, null, null, null, 0, 999);

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
        verify(gateway).page(null, null, null, null, null, 1, 100);
    }

    @Test
    @DisplayName("action / targetType 去首尾空白后精确下传；纯空白视为不筛选，targetId 原样下传")
    void actionAndTargetTypeAreTrimmedAndBlankFallsBackToNull() {
        when(gateway.page(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageResult<>(List.of(), 0, 1, 20));

        service.page("  USER_BAN  ", 7L, "  users ", 42L, 30, 1, 20);
        verify(gateway).page("USER_BAN", 7L, "users", 42L, 30, 1, 20);

        service.page("   ", null, "  ", null, null, 1, 20);
        verify(gateway).page(null, null, null, null, null, 1, 20);
    }

    @Test
    @DisplayName("days 非 7 / 30 / 90 → 1001，且不触达网关")
    void invalidDaysIsRejected() {
        assertThatThrownBy(() -> service.page(null, null, null, null, 8, 1, 20))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));
        verify(gateway, never()).page(any(), any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("action 超过 64 字（列宽）→ 1001，且不触达网关")
    void tooLongActionIsRejected() {
        assertThatThrownBy(() -> service.page("A".repeat(65), null, null, null, null, 1, 20))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));
        verify(gateway, never()).page(any(), any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("targetType 超过 32 字（列宽）→ 1001，且不触达网关")
    void tooLongTargetTypeIsRejected() {
        assertThatThrownBy(() -> service.page(null, null, "T".repeat(33), null, null, 1, 20))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));
        verify(gateway, never()).page(any(), any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("操作者展示名兜底：actorId 为空 → 系统；昵称缺失 → 已注销用户")
    void actorNicknameFallsBack() {
        when(gateway.page(isNull(), isNull(), isNull(), isNull(), isNull(), anyInt(), anyInt())).thenReturn(
                new PageResult<>(List.of(row(null, null, "ACCOUNT_ANONYMIZE"),
                        row(ACTOR_ID, "  ", "ROSTER_IMPORT")), 2, 1, 20));

        List<AuditItem> items = service.page(null, null, null, null, null, 1, 20).items();

        assertThat(items.get(0).actorNickname()).isEqualTo("系统");
        assertThat(items.get(1).actorNickname()).isEqualTo("已注销用户");
        assertThat(items.get(0).actorId()).isNull();
        assertThat(items.get(1).actorId()).isEqualTo(ACTOR_ID);
    }

    @Test
    @DisplayName("导出：BOM + 表头 + CRLF；逗号 / 引号 / 换行按 RFC 4180 转义；= 开头前置单引号防公式注入")
    void exportWritesBomHeaderAndEscapesCells() {
        AuditRow plain = new AuditRow(11L, ACTOR_ID, "管理员甲", "USER_BAN", "users", 42L,
                "reason=灌水", CREATED_AT);
        AuditRow tricky = new AuditRow(12L, null, null, "ROSTER_IMPORT", "student_roster", null,
                "=1+1,\"quoted\"\nnext", Instant.parse("2026-11-10T09:30:05Z"));
        when(gateway.list(isNull(), isNull(), isNull(), isNull(), isNull(), eq(5001)))
                .thenReturn(List.of(plain, tricky));

        AuditExport export = service.export(null, null, null, null, null);
        String csv = new String(export.content(), StandardCharsets.UTF_8);

        assertThat(export.filename()).matches("audit-logs-\\d{8}-\\d{6}\\.csv");
        assertThat(csv).startsWith("﻿时间(UTC),操作者ID,操作者昵称,动作,目标类型,目标ID,详情\r\n");
        assertThat(csv).contains("2026-11-10 08:00:00,1,管理员甲,USER_BAN,users,42,reason=灌水\r\n");
        assertThat(csv).contains("2026-11-10 09:30:05,,系统,ROSTER_IMPORT,student_roster,,")
                .contains("\"'=1+1,\"\"quoted\"\"\nnext\"");
    }

    @Test
    @DisplayName("导出空结果仍返回表头（200 语义），不因空页报错")
    void exportEmptyStillReturnsHeader() {
        when(gateway.list(isNull(), isNull(), isNull(), isNull(), isNull(), eq(5001)))
                .thenReturn(List.of());

        String csv = new String(service.export(null, null, null, null, null).content(), StandardCharsets.UTF_8);

        assertThat(csv).isEqualTo("﻿时间(UTC),操作者ID,操作者昵称,动作,目标类型,目标ID,详情\r\n");
    }

    @Test
    @DisplayName("导出超过 5000 行 → 1001（不静默截断）")
    void exportRejectsMoreThanLimit() {
        List<AuditRow> tooMany = IntStream.range(0, 5001)
                .mapToObj(i -> row(ACTOR_ID, "管理员甲", "USER_BAN"))
                .toList();
        when(gateway.list(isNull(), isNull(), isNull(), isNull(), isNull(), eq(5001)))
                .thenReturn(tooMany);

        assertThatThrownBy(() -> service.export(null, null, null, null, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));
    }

    @Test
    @DisplayName("导出沿用同一套参数校验：非法 days / 超长 targetType 均 1001，且不触达网关")
    void exportValidatesFilters() {
        assertThatThrownBy(() -> service.export(null, null, null, null, 365))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));
        assertThatThrownBy(() -> service.export(null, null, "T".repeat(33), null, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));
        verify(gateway, never()).list(any(), any(), any(), any(), any(), anyInt());
    }

    private static AuditRow row(Long actorId, String actorNickname, String action) {
        return new AuditRow(AUDIT_ID, actorId, actorNickname, action, "users", 42L, "reason=灌水", CREATED_AT);
    }
}
