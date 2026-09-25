package com.campuslink.module.forum.application;

import com.campuslink.common.audit.AuditService;
import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.account.application.AccountApplicationService;
import com.campuslink.module.forum.domain.gateway.PageResult;
import com.campuslink.module.forum.domain.gateway.PostRepository;
import com.campuslink.module.forum.domain.gateway.ReportRepository;
import com.campuslink.module.forum.domain.gateway.ReplyRepository;
import com.campuslink.module.forum.domain.model.PostStatus;
import com.campuslink.module.forum.domain.model.Report;
import com.campuslink.module.forum.domain.model.ReplyStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

/**
 * 举报与工单闭环（F-SAFE-002 / CR-082）：
 * 提交（24h 同对象去重 → 2012）与运营处理（REMOVE 联动帖子 / 楼层 / 用户处置；DISMISS 驳回）。
 * 全部闭环动作写审计；工单已闭环时重复处理幂等 200、不再记审计（与 CR-066 处置纪律一致）。
 */
@Service
@RequiredArgsConstructor
public class ReportApplicationService {

    private static final Set<String> TARGET_TYPES = Set.of(Report.TARGET_POST, Report.TARGET_REPLY, Report.TARGET_USER);
    private static final Set<String> REASONS = Set.of("ILLEGAL", "HARASS", "COPYRIGHT", "SPAM", "OTHER");
    private static final long DEDUP_WINDOW_SECONDS = 24L * 3600;

    private final ReportRepository reportRepository;
    private final PostRepository postRepository;
    private final ReplyRepository replyRepository;
    private final AccountApplicationService accountApplicationService;
    private final AuditService auditService;

    /** 提交举报：被举报内容对举报人即时不可见由读侧后续 CR 承接；本用例只负责建单与去重 */
    public Report submit(long reporterId, String targetType, Long targetId, String reason, String detail) {
        if (!TARGET_TYPES.contains(targetType)) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
        if (reason == null || !REASONS.contains(reason)) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
        if (reportRepository.existsRecentByReporterAndTarget(reporterId, targetType, targetId,
                Instant.now().minusSeconds(DEDUP_WINDOW_SECONDS))) {
            throw new ApiException(ResultCode.REPORT_DUPLICATED);
        }
        Report report = reportRepository.save(new Report(null, reporterId, targetType, targetId, reason,
                detail, "PENDING", null, null, Instant.now(), null));
        auditService.record(reporterId, "REPORT_SUBMIT", targetType, targetId, "reportId=" + report.id());
        return report;
    }

    /** 工单分页（运营）：status 可选 PENDING / CLOSED，缺省全部 */
    public PageResult<Report> adminPage(String status, int page, int size) {
        return reportRepository.findPage(status, Math.max(page, 1), Math.min(Math.max(size, 1), 100));
    }

    /**
     * 工单处理（运营 / 超管）：REMOVE = 对目标执行处置（帖子 / 楼层下架走 CR-066 定向 UPDATE，
     * 用户走封禁），DISMISS = 驳回；随后工单闭环 + 审计。重复处理（已 CLOSED）幂等 200、零审计。
     */
    @Transactional
    public void close(long operatorId, long reportId, String action, String note) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        if (report.isClosed()) {
            return;
        }
        if (!"REMOVE".equals(action) && !"DISMISS".equals(action)) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
        if ("REMOVE".equals(action)) {
            removeTarget(report, operatorId);
        }
        String resolution = (action + (note == null || note.isBlank() ? "" : "：" + note));
        if (!reportRepository.close(reportId, resolution, operatorId)) {
            // 并发下别人先闭环了：幂等收场，不重复审计
            return;
        }
        auditService.record(operatorId, "REPORT_CLOSE", report.targetType(), report.targetId(),
                "reportId=%s action=%s".formatted(reportId, action));
    }

    private void removeTarget(Report report, long operatorId) {
        switch (report.targetType()) {
            case Report.TARGET_POST -> postRepository.updateStatus(report.targetId(), PostStatus.REMOVED, PostStatus.PUBLISHED);
            case Report.TARGET_REPLY -> replyRepository.updateStatus(report.targetId(), ReplyStatus.REMOVED, ReplyStatus.PUBLISHED);
            case Report.TARGET_USER -> accountApplicationService.adminBan(operatorId, report.targetId());
            default -> throw new ApiException(ResultCode.INVALID_PARAM);
        }
    }
}
