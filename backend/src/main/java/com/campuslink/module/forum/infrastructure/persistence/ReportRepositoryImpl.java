package com.campuslink.module.forum.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campuslink.module.forum.domain.gateway.PageResult;
import com.campuslink.module.forum.domain.gateway.ReportRepository;
import com.campuslink.module.forum.domain.model.Report;
import com.campuslink.module.forum.infrastructure.persistence.mapper.ReportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

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
