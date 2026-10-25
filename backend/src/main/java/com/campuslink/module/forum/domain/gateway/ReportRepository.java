package com.campuslink.module.forum.domain.gateway;

import com.campuslink.module.forum.domain.model.Report;
import com.campuslink.module.forum.domain.model.ReportedTargets;

import java.time.Instant;
import java.util.Optional;

/** 出站端口：举报工单（F-SAFE-002 / CR-082）。24h 去重、状态闭环（PENDING → CLOSED）在实现侧保证 */
public interface ReportRepository {

    Report save(Report report);

    /** 同一举报人对同一对象的既有工单（24h 窗口内），用于提交去重 */
    boolean existsRecentByReporterAndTarget(Long reporterId, String targetType, Long targetId, Instant since);

    /**
     * 举报人当前仍待处理（{@code PENDING}）的举报目标集合（CR-140）：按帖子 / 楼层两类归并。
     *
     * <p>工单闭环后不再返回：{@code DISMISS} 驳回的内容对举报人恢复可见；{@code REMOVE} 处置的内容
     * 本身已下架 / 封禁，无需再靠本集合隐藏。用户类目标不进入结果（无内容可隐，见 {@link ReportedTargets}）。
     */
    ReportedTargets findPendingTargetsByReporter(Long reporterId);

    Optional<Report> findById(Long id);

    /** 工单分页：status 为 null 表示全部 */
    PageResult<Report> findPage(String status, int page, int size);

    /** 闭环：status = CLOSED + resolution + handler + closed_at；未闭环行才被更新（幂等由应用层判定） */
    boolean close(Long id, String resolution, Long handlerId);
}
