package com.campuslink.module.forum.infrastructure.persistence;

import com.campuslink.module.forum.domain.gateway.AdminStatsGateway;
import com.campuslink.module.forum.infrastructure.persistence.mapper.AdminStatsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;

/** 适配器：AdminStatsGateway 的 MyBatis-Plus 实现（F-ADMIN-005 / CR-131） */
@Repository
@RequiredArgsConstructor
public class AdminStatsGatewayImpl implements AdminStatsGateway {

    private final AdminStatsMapper adminStatsMapper;

    @Override
    public ContentStats postStats(Instant todayStart) {
        return toContent(adminStatsMapper.postStats(todayStart));
    }

    @Override
    public ContentStats replyStats(Instant todayStart) {
        return toContent(adminStatsMapper.replyStats(todayStart));
    }

    @Override
    public ReportStats reportStats() {
        ReportStatsRow row = adminStatsMapper.reportStats();
        return new ReportStats(value(row.getTotal()), value(row.getPending()), value(row.getClosed()));
    }

    private static ContentStats toContent(ContentStatsRow row) {
        return new ContentStats(value(row.getTotal()), value(row.getPublished()),
                value(row.getRemoved()), value(row.getTodayCreated()));
    }

    /** 空表时 COUNT 为 0、SUM 由 SQL 的 COALESCE 兜底；再防一层 null 只为不把 NPE 留在展示层 */
    private static long value(Long v) {
        return v == null ? 0L : v;
    }
}
