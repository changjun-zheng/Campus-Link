package com.campuslink.module.forum.domain.gateway;

import com.campuslink.module.forum.domain.model.Report;

import java.time.Instant;
import java.util.Optional;

/** 出站端口：举报工单（F-SAFE-002 / CR-082）。24h 去重、状态闭环（PENDING → CLOSED）在实现侧保证 */
public interface ReportRepository {

    Report save(Report report);

    /** 同一举报人对同一对象的既有工单（24h 窗口内），用于提交去重 */
    boolean existsRecentByReporterAndTarget(Long reporterId, String targetType, Long targetId, Instant since);

    Optional<Report> findById(Long id);

    /** 工单分页：status 为 null 表示全部 */
    PageResult<Report> findPage(String status, int page, int size);

    /** 闭环：status = CLOSED + resolution + handler + closed_at；未闭环行才被更新（幂等由应用层判定） */
    boolean close(Long id, String resolution, Long handlerId);
}
