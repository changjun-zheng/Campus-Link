package com.campuslink.module.forum.application;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.forum.domain.gateway.AdminAuditGateway;
import com.campuslink.module.forum.domain.gateway.AdminAuditGateway.AuditRow;
import com.campuslink.module.forum.domain.gateway.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 操作审计只读用例（CR-215）：把 {@code audit_logs} 的检索口径收敛到一处。
 *
 * <p>口径：
 * <ul>
 *   <li>{@code action} 去首尾空白，空串视为不筛选；超过 64 字（列宽）→ {@code 1001}；</li>
 *   <li>{@code days} 只接受 7 / 30 / 90，其余 {@code 1001}（与后台内容搜索同窗口，不静默归一）；</li>
 *   <li>page 归一到 ≥1，size 归一到 1~100；</li>
 *   <li>操作者展示名：{@code actorId} 为空 → 「系统」；账号行缺失 → 「已注销用户」。</li>
 * </ul>
 *
 * <p>本服务不写审计——写入仍只经 {@code common.audit.AuditService}，避免出现第二条写路径。
 */
@Service
@RequiredArgsConstructor
public class AdminAuditApplicationService {

    private static final Set<Integer> TIME_WINDOWS = Set.of(7, 30, 90);
    private static final int ACTION_MAX_CHARS = 64;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String SYSTEM_ACTOR = "系统";
    private static final String MISSING_ACTOR = "已注销用户";

    private final AdminAuditGateway adminAuditGateway;

    public PageResult<AuditItem> page(String action, Long actorId, Integer days, int page, int size) {
        String normalizedAction = action == null ? "" : action.trim();
        if (normalizedAction.length() > ACTION_MAX_CHARS) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
        if (days != null && !TIME_WINDOWS.contains(days)) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
        int currentPage = Math.max(page, 1);
        int pageSize = size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);

        PageResult<AuditRow> found = adminAuditGateway.page(
                normalizedAction.isEmpty() ? null : normalizedAction, actorId, days, currentPage, pageSize);
        List<AuditItem> items = found.items().stream().map(AdminAuditApplicationService::toItem).toList();
        return new PageResult<>(items, found.total(), currentPage, pageSize);
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
}
