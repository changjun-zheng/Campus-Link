package com.campuslink.module.forum.infrastructure.persistence;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campuslink.module.forum.domain.gateway.PageResult;
import com.campuslink.module.forum.domain.model.Report;
import com.campuslink.module.forum.infrastructure.persistence.mapper.ReportMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code ReportRepositoryImpl} 的举报工单仓储（F-SAFE-002 / CR-082，CR-111 覆盖率批）。
 *
 * <p>守三条口径：① 24h 去重的 WHERE 必须同时含举报人 / 目标类型 / 目标 id / 时间窗起点，
 * 少任何一条都会退化成「全站维度去重」；② 运营分页的 status 是**可选**条件，空白串不得拼进 WHERE
 * （否则「全部」页会变成「status=''」的空页）；③ 闭环 UPDATE 的 WHERE 带 {@code status='PENDING'}——
 * 这是「两次运营同时处理同一工单只生效一次」的唯一依据，返回 false 时应用层必须跳过审计。
 *
 * <p>本类不证明数据库执行结果：断言落在 wrapper 的 SQL 片段与参数值上。
 */
@ExtendWith(MockitoExtension.class)
class ReportRepositoryImplTest {

    private static final long REPORT_ID = 9L;
    private static final long REPORTER_ID = 42L;
    private static final long TARGET_ID = 7L;
    private static final long HANDLER_ID = 1L;
    private static final Instant SINCE = Instant.parse("2026-10-14T08:00:00Z");

    @Mock
    private ReportMapper reportMapper;

    private ReportRepositoryImpl repository;

    @BeforeAll
    static void initTableInfoCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(ReportMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, ReportDO.class);
    }

    @BeforeEach
    void setUp() {
        repository = new ReportRepositoryImpl(reportMapper);
    }

    @Test
    @DisplayName("建单：领域对象转 DO 后 insert，再用回填后的 DO 转回（id 由库自增）")
    void saveRoundTripsThroughDo() {
        when(reportMapper.insert(any(ReportDO.class))).thenAnswer(inv -> {
            inv.getArgument(0, ReportDO.class).setId(REPORT_ID);
            return 1;
        });

        Report saved = repository.save(pendingReport());

        ArgumentCaptor<ReportDO> captor = ArgumentCaptor.forClass(ReportDO.class);
        verify(reportMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("PENDING");
        assertThat(captor.getValue().getTargetType()).isEqualTo(Report.TARGET_POST);
        assertThat(saved.id()).isEqualTo(REPORT_ID);
    }

    @Test
    @DisplayName("24h 去重：WHERE 含举报人 / 目标类型 / 目标 id / created_at >= since 四项")
    void existsRecentFiltersOnAllFourConditions() {
        when(reportMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        assertThat(repository.existsRecentByReporterAndTarget(REPORTER_ID, Report.TARGET_POST, TARGET_ID, SINCE))
                .isTrue();

        LambdaQueryWrapper<ReportDO> query = capturedSelectCount();
        String sql = query.getSqlSegment();
        assertThat(sql).contains("reporter_id", "target_type", "target_id", "created_at", ">=");
        assertThat(query.getParamNameValuePairs().values())
                .contains(REPORTER_ID, Report.TARGET_POST, TARGET_ID, SINCE);
    }

    @Test
    @DisplayName("按 id 查：查不到回 Optional.empty，查到完整映射回 record")
    void findByIdMapsOrEmpty() {
        when(reportMapper.selectById(REPORT_ID)).thenReturn(null);
        assertThat(repository.findById(REPORT_ID)).isEmpty();

        when(reportMapper.selectById(REPORT_ID)).thenReturn(doRow());
        assertThat(repository.findById(REPORT_ID)).map(Report::id).hasValue(REPORT_ID);
    }

    @Test
    @DisplayName("运营分页：status 为 null / 空白时不写 status 条件（全部工单），仍按 created_at + id 倒序")
    @SuppressWarnings("unchecked")
    void findPageWithoutStatusListsAll() {
        when(reportMapper.selectPage(any(IPage.class), any(Wrapper.class)))
                .thenReturn(pageOf(List.of(doRow()), 4));

        PageResult<Report> result = repository.findPage("  ", 1, 20);

        assertThat(result.total()).isEqualTo(4);
        assertThat(result.items()).hasSize(1);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(20);

        LambdaQueryWrapper<ReportDO> query = capturedPageQuery();
        String sql = query.getSqlSegment();
        assertThat(sql).doesNotContain("status");
        assertThat(sql).contains("created_at", "id", "ORDER BY");
    }

    @Test
    @DisplayName("运营分页：status=PENDING 时进 WHERE，绑定值原样")
    @SuppressWarnings("unchecked")
    void findPageWithStatusFilters() {
        when(reportMapper.selectPage(any(IPage.class), any(Wrapper.class)))
                .thenReturn(pageOf(List.of(), 0));

        repository.findPage("PENDING", 2, 10);

        LambdaQueryWrapper<ReportDO> query = capturedPageQuery();
        assertThat(query.getSqlSegment()).contains("status");
        assertThat(query.getParamNameValuePairs().values()).contains("PENDING");
        assertThat(query.getSqlSegment()).contains("ORDER BY");
    }

    @Test
    @DisplayName("闭环：WHERE 带 id + status=PENDING（并发只生效一次），SET 写 CLOSED / resolution / handler / closed_at")
    @SuppressWarnings("unchecked")
    void closeIsGuardedByPendingStatus() {
        when(reportMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        assertThat(repository.close(REPORT_ID, "REMOVE", HANDLER_ID)).isTrue();

        ArgumentCaptor<Wrapper<ReportDO>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(reportMapper).update(isNull(), captor.capture());
        LambdaUpdateWrapper<ReportDO> update = (LambdaUpdateWrapper<ReportDO>) captor.getValue();

        assertThat(update.getSqlSet()).contains("status", "resolution", "handler_id", "closed_at");
        assertThat(update.getSqlSegment()).contains("id", "status");
        assertThat(update.getParamNameValuePairs().values())
                .contains("PENDING", "CLOSED", "REMOVE", HANDLER_ID);
        assertThat(update.getParamNameValuePairs().values().stream()
                .filter(Instant.class::isInstance)).isNotEmpty();
    }

    @Test
    @DisplayName("闭环返回 false（工单已被别人先关）：原样回 false，调用方据此跳过审计")
    @SuppressWarnings("unchecked")
    void closeReturnsFalseWhenNoRowMatches() {
        when(reportMapper.update(isNull(), any(Wrapper.class))).thenReturn(0);

        assertThat(repository.close(REPORT_ID, "DISMISS", HANDLER_ID)).isFalse();
    }

    // ---------- helpers ----------

    private static Report pendingReport() {
        return new Report(null, REPORTER_ID, Report.TARGET_POST, TARGET_ID, "SPAM", "广告",
                "PENDING", null, null, SINCE, null);
    }

    private static ReportDO doRow() {
        ReportDO d = new ReportDO();
        d.setId(REPORT_ID);
        d.setReporterId(REPORTER_ID);
        d.setTargetType(Report.TARGET_POST);
        d.setTargetId(TARGET_ID);
        d.setReason("SPAM");
        d.setDetail("广告");
        d.setStatus("PENDING");
        d.setCreatedAt(SINCE);
        return d;
    }

    private static IPage<ReportDO> pageOf(List<ReportDO> rows, long total) {
        Page<ReportDO> page = new Page<>(1, 20);
        page.setRecords(rows);
        page.setTotal(total);
        return page;
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<ReportDO> capturedSelectCount() {
        ArgumentCaptor<Wrapper<ReportDO>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(reportMapper, org.mockito.Mockito.atLeastOnce()).selectCount(captor.capture());
        return (LambdaQueryWrapper<ReportDO>) captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<ReportDO> capturedPageQuery() {
        ArgumentCaptor<Wrapper<ReportDO>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(reportMapper, org.mockito.Mockito.atLeastOnce()).selectPage(any(IPage.class), captor.capture());
        return (LambdaQueryWrapper<ReportDO>) captor.getValue();
    }
}
