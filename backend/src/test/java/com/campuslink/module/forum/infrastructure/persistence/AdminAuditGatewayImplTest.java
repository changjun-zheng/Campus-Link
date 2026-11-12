package com.campuslink.module.forum.infrastructure.persistence;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campuslink.module.forum.domain.gateway.AdminAuditGateway.AuditRow;
import com.campuslink.module.forum.domain.gateway.PageResult;
import com.campuslink.module.forum.infrastructure.persistence.mapper.AdminAuditMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 操作审计适配器（CR-215 / CR-216）：分页 / 导出参数原样下传、查询行到窄载体的映射、page / size 回显。
 *
 * <p>SQL 本身由 {@code AdminAuditMapper} 的 {@code @Select} 承载，本类不连库；断言落在
 * 「传进去的页参数与筛选值」与「映射后的载体」上。
 */
@ExtendWith(MockitoExtension.class)
class AdminAuditGatewayImplTest {

    @Mock
    private AdminAuditMapper adminAuditMapper;

    @InjectMocks
    private AdminAuditGatewayImpl gateway;

    @Test
    @DisplayName("筛选与分页原样下传：action / actorId / targetType / targetId / days / page / size，结果映射为 AuditRow")
    @SuppressWarnings("unchecked")
    void filtersAndPageArePassedThroughAndRowsMapped() {
        when(adminAuditMapper.page(any(IPage.class), any(), any(), any(), any(), any()))
                .thenReturn(pageOf(List.of(row()), 3));

        PageResult<AuditRow> result = gateway.page("USER_BAN", 7L, "users", 42L, 30, 2, 50);

        ArgumentCaptor<IPage<AdminAuditRowDO>> captor = ArgumentCaptor.forClass(IPage.class);
        verify(adminAuditMapper).page(captor.capture(), eq("USER_BAN"), eq(7L), eq("users"), eq(42L), eq(30));
        assertThat(captor.getValue().getCurrent()).isEqualTo(2);
        assertThat(captor.getValue().getSize()).isEqualTo(50);
        assertThat(result.total()).isEqualTo(3);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(50);
        assertMapped(result.items().get(0));
    }

    @Test
    @DisplayName("无筛选：全部条件以 null 下传，空结果映射为空列表")
    void nullFiltersArePassedThrough() {
        when(adminAuditMapper.page(any(IPage.class), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(pageOf(List.of(), 0));

        PageResult<AuditRow> result = gateway.page(null, null, null, null, null, 1, 20);

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isZero();
        verify(adminAuditMapper).page(any(IPage.class), isNull(), isNull(), isNull(), isNull(), isNull());
    }

    @Test
    @DisplayName("导出列表：同筛选 + limit 原样下传，行映射为 AuditRow")
    void listPassesFiltersAndLimit() {
        when(adminAuditMapper.list(eq("USER_BAN"), eq(7L), eq("users"), eq(42L), eq(30), eq(5001)))
                .thenReturn(List.of(row()));

        List<AuditRow> rows = gateway.list("USER_BAN", 7L, "users", 42L, 30, 5001);

        assertThat(rows).hasSize(1);
        assertMapped(rows.get(0));
        verify(adminAuditMapper).list("USER_BAN", 7L, "users", 42L, 30, 5001);
    }

    private static void assertMapped(AuditRow mapped) {
        assertThat(mapped.id()).isEqualTo(11L);
        assertThat(mapped.actorId()).isEqualTo(7L);
        assertThat(mapped.actorNickname()).isEqualTo("管理员甲");
        assertThat(mapped.action()).isEqualTo("USER_BAN");
        assertThat(mapped.targetType()).isEqualTo("users");
        assertThat(mapped.targetId()).isEqualTo(42L);
        assertThat(mapped.detail()).isEqualTo("reason=灌水");
        assertThat(mapped.createdAt()).isEqualTo(Instant.parse("2026-11-10T08:00:00Z"));
    }

    private static AdminAuditRowDO row() {
        AdminAuditRowDO row = new AdminAuditRowDO();
        row.setId(11L);
        row.setActorId(7L);
        row.setActorNickname("管理员甲");
        row.setAction("USER_BAN");
        row.setTargetType("users");
        row.setTargetId(42L);
        row.setDetail("reason=灌水");
        row.setCreatedAt(Instant.parse("2026-11-10T08:00:00Z"));
        return row;
    }

    private static IPage<AdminAuditRowDO> pageOf(List<AdminAuditRowDO> rows, long total) {
        Page<AdminAuditRowDO> page = new Page<>(1, 20);
        page.setRecords(rows);
        page.setTotal(total);
        return page;
    }
}
