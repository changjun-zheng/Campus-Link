package com.campuslink.module.forum.application;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.forum.domain.gateway.AdminAuditGateway;
import com.campuslink.module.forum.domain.gateway.AdminAuditGateway.AuditRow;
import com.campuslink.module.forum.domain.gateway.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * 操作审计只读用例（CR-215 / CR-216）：把 {@code audit_logs} 的检索口径收敛到一处。
 *
 * <p>口径：
 * <ul>
 *   <li>{@code action} 去首尾空白，空串视为不筛选；超过 64 字（列宽）→ {@code 1001}；</li>
 *   <li>{@code targetType} 同法归一，超过 32 字（列宽）→ {@code 1001}；{@code targetId} 原样下传；</li>
 *   <li>{@code days} 只接受 7 / 30 / 90，其余 {@code 1001}（与后台内容搜索同窗口，不静默归一）；</li>
 *   <li>page 归一到 ≥1，size 归一到 1~100；</li>
 *   <li>操作者展示名：{@code actorId} 为空 → 「系统」；账号行缺失 → 「已注销用户」。</li>
 * </ul>
 *
 * <p>导出（CR-216）：与分页同筛选、同排序，最多 {@value #EXPORT_MAX_ROWS} 行，超出直接
 * {@code 1001}（不静默截断）；CSV 带 UTF-8 BOM（Excel 直接识别中文），并按 RFC 4180
 * 转义引号 / 逗号 / 换行，对 {@code = + - @ Tab CR} 开头的单元格前置单引号防公式注入。
 *
 * <p>本服务不写审计——写入仍只经 {@code common.audit.AuditService}，避免出现第二条写路径。
 */
@Service
@RequiredArgsConstructor
public class AdminAuditApplicationService {

    private static final Set<Integer> TIME_WINDOWS = Set.of(7, 30, 90);
    private static final int ACTION_MAX_CHARS = 64;
    private static final int TARGET_TYPE_MAX_CHARS = 32;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int EXPORT_MAX_ROWS = 5000;
    private static final String SYSTEM_ACTOR = "系统";
    private static final String MISSING_ACTOR = "已注销用户";
    private static final String CSV_BOM = "﻿";
    private static final String CSV_HEADER = "时间(UTC),操作者ID,操作者昵称,动作,目标类型,目标ID,详情";
    private static final DateTimeFormatter CSV_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final AdminAuditGateway adminAuditGateway;

    public PageResult<AuditItem> page(String action, Long actorId, String targetType, Long targetId,
                                      Integer days, int page, int size) {
        String normalizedAction = normalize(action, ACTION_MAX_CHARS);
        String normalizedTargetType = normalize(targetType, TARGET_TYPE_MAX_CHARS);
        validateDays(days);
        int currentPage = Math.max(page, 1);
        int pageSize = size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);

        PageResult<AuditRow> found = adminAuditGateway.page(normalizedAction, actorId,
                normalizedTargetType, targetId, days, currentPage, pageSize);
        List<AuditItem> items = found.items().stream().map(AdminAuditApplicationService::toItem).toList();
        return new PageResult<>(items, found.total(), currentPage, pageSize);
    }

    /**
     * CSV 导出：返回文件名与字节；调用方负责响应头。超过行上限时抛 {@code 1001}
     * （提示缩小筛选），不做静默截断——静默截断会让「已归档证据」少行而不自知。
     */
    public AuditExport export(String action, Long actorId, String targetType, Long targetId, Integer days) {
        String normalizedAction = normalize(action, ACTION_MAX_CHARS);
        String normalizedTargetType = normalize(targetType, TARGET_TYPE_MAX_CHARS);
        validateDays(days);

        List<AuditRow> rows = adminAuditGateway.list(normalizedAction, actorId, normalizedTargetType,
                targetId, days, EXPORT_MAX_ROWS + 1);
        if (rows.size() > EXPORT_MAX_ROWS) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
        return new AuditExport("audit-logs-" + FILE_TIME.format(Instant.now()) + ".csv",
                toCsv(rows).getBytes(StandardCharsets.UTF_8));
    }

    private static String toCsv(List<AuditRow> rows) {
        StringBuilder csv = new StringBuilder(CSV_BOM).append(CSV_HEADER).append("\r\n");
        for (AuditRow row : rows) {
            csv.append(csvCell(CSV_TIME.format(row.createdAt()))).append(',')
                    .append(csvCell(row.actorId() == null ? "" : String.valueOf(row.actorId()))).append(',')
                    .append(csvCell(actorNickname(row))).append(',')
                    .append(csvCell(row.action())).append(',')
                    .append(csvCell(row.targetType())).append(',')
                    .append(csvCell(row.targetId() == null ? "" : String.valueOf(row.targetId()))).append(',')
                    .append(csvCell(row.detail())).append("\r\n");
        }
        return csv.toString();
    }

    /** RFC 4180 转义 + 公式注入防护（单元格首字符触发 Excel/WPS 公式时必须前置单引号） */
    private static String csvCell(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String cell = value;
        char first = cell.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            cell = "'" + cell;
        }
        if (cell.indexOf(',') >= 0 || cell.indexOf('"') >= 0
                || cell.indexOf('\n') >= 0 || cell.indexOf('\r') >= 0) {
            cell = "\"" + cell.replace("\"", "\"\"") + "\"";
        }
        return cell;
    }

    private static String normalize(String value, int maxChars) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > maxChars) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private static void validateDays(Integer days) {
        if (days != null && !TIME_WINDOWS.contains(days)) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
    }

    private static AuditItem toItem(AuditRow row) {
        return new AuditItem(row.id(), row.actorId(), actorNickname(row), row.action(), row.targetType(),
                row.targetId(), row.detail(), row.createdAt());
    }

    /** 展示名兜底：actor 为空是系统动作；LEFT JOIN 不到账号行（账号数据缺失）沿用「已注销用户」口径 */
    private static String actorNickname(AuditRow row) {
        if (row.actorId() == null) {
            return SYSTEM_ACTOR;
        }
        return row.actorNickname() == null || row.actorNickname().isBlank()
                ? MISSING_ACTOR : row.actorNickname();
    }

    /** 审计条目（application 层载体；web 层再映射成 VO） */
    public record AuditItem(long id, Long actorId, String actorNickname, String action, String targetType,
                            Long targetId, String detail, Instant createdAt) {
    }

    /** 导出结果：文件名（UTC 时间戳）与 CSV 字节（已含 BOM）；防御性拷贝沿用图片载体的既有写法 */
    public record AuditExport(String filename, byte[] content) {
        public AuditExport {
            content = content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }
}
