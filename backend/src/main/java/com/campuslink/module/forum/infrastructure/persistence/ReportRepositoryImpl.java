package com.campuslink.module.forum.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campuslink.module.forum.domain.gateway.PageResult;
import com.campuslink.module.forum.domain.gateway.ReportRepository;
import com.campuslink.module.forum.domain.model.Report;
import com.campuslink.module.forum.domain.model.ReportedTargets;
import com.campuslink.module.forum.infrastructure.persistence.mapper.ReportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 适配器：ReportRepository 端口的 MyBatis-Plus 实现 */
@Repository
@RequiredArgsConstructor
public class ReportRepositoryImpl implements ReportRepository {

    private final ReportMapper reportMapper;

    @Override
    public Report save(Report report) {
        ReportDO d = ReportConverter.toDo(report);
        reportMapper.insert(d);
        return ReportConverter.toDomain(d);
    }

    @Override
    public boolean existsRecentByReporterAndTarget(Long reporterId, String targetType, Long targetId, Instant since) {
        return reportMapper.selectCount(new LambdaQueryWrapper<ReportDO>()
                .eq(ReportDO::getReporterId, reporterId)
                .eq(ReportDO::getTargetType, targetType)
                .eq(ReportDO::getTargetId, targetId)
                .ge(ReportDO::getCreatedAt, since)) > 0;
    }

    @Override
    public ReportedTargets findPendingTargetsByReporter(Long reporterId) {
        if (reporterId == null) {
            return ReportedTargets.EMPTY;
        }
        // 只取两列：读侧过滤只需要类型与目标 id，不装配工单聚合（同 CR-077 Warning #7 的窄载体纪律）
        List<ReportDO> rows = reportMapper.selectList(new LambdaQueryWrapper<ReportDO>()
                .select(ReportDO::getTargetType, ReportDO::getTargetId)
                .eq(ReportDO::getReporterId, reporterId)
                .eq(ReportDO::getStatus, "PENDING"));
        Set<Long> postIds = new HashSet<>();
        Set<Long> replyIds = new HashSet<>();
        for (ReportDO row : rows) {
            if (row.getTargetId() == null) {
                continue;
            }
            if (Report.TARGET_POST.equals(row.getTargetType())) {
                postIds.add(row.getTargetId());
            } else if (Report.TARGET_REPLY.equals(row.getTargetType())) {
                replyIds.add(row.getTargetId());
            }
            // USER 类目标不入集合：用户没有"内容"可隐，读取侧不参与过滤
        }
        if (postIds.isEmpty() && replyIds.isEmpty()) {
            return ReportedTargets.EMPTY;
        }
        return new ReportedTargets(postIds, replyIds);
    }

    @Override
    public Optional<Report> findById(Long id) {
        return Optional.ofNullable(reportMapper.selectById(id)).map(ReportConverter::toDomain);
    }

    @Override
    public PageResult<Report> findPage(String status, int page, int size) {
        LambdaQueryWrapper<ReportDO> query = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            query.eq(ReportDO::getStatus, status);
        }
        query.orderByDesc(ReportDO::getCreatedAt).orderByDesc(ReportDO::getId);
        IPage<ReportDO> result = reportMapper.selectPage(new Page<>(page, size), query);
        return new PageResult<>(result.getRecords().stream().map(ReportConverter::toDomain).toList(),
                result.getTotal(), page, size);
    }

    @Override
    public boolean close(Long id, String resolution, Long handlerId) {
        return reportMapper.update(null, new LambdaUpdateWrapper<ReportDO>()
                .eq(ReportDO::getId, id)
                .eq(ReportDO::getStatus, "PENDING")
                .set(ReportDO::getStatus, "CLOSED")
                .set(ReportDO::getResolution, resolution)
                .set(ReportDO::getHandlerId, handlerId)
                .set(ReportDO::getClosedAt, Instant.now())) > 0;
    }
}
