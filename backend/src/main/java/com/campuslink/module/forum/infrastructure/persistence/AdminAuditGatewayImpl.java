package com.campuslink.module.forum.infrastructure.persistence;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campuslink.module.forum.domain.gateway.AdminAuditGateway;
import com.campuslink.module.forum.domain.gateway.PageResult;
import com.campuslink.module.forum.infrastructure.persistence.mapper.AdminAuditMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/** 适配器：AdminAuditGateway 的 MyBatis-Plus 实现（CR-215 / CR-216，只读） */
@Repository
@RequiredArgsConstructor
public class AdminAuditGatewayImpl implements AdminAuditGateway {

    private final AdminAuditMapper adminAuditMapper;

    @Override
    public PageResult<AuditRow> page(String action, Long actorId, String targetType, Long targetId,
                                     Integer days, int page, int size) {
        IPage<AdminAuditRowDO> result = adminAuditMapper.page(
                new Page<>(page, size), action, actorId, targetType, targetId, days);
        return new PageResult<>(result.getRecords().stream().map(AdminAuditGatewayImpl::toRow).toList(),
                result.getTotal(), page, size);
    }

    @Override
    public List<AuditRow> list(String action, Long actorId, String targetType, Long targetId,
                               Integer days, int limit) {
        return adminAuditMapper.list(action, actorId, targetType, targetId, days, limit).stream()
                .map(AdminAuditGatewayImpl::toRow)
                .toList();
    }

    private static AuditRow toRow(AdminAuditRowDO row) {
        return new AuditRow(row.getId(), row.getActorId(), row.getActorNickname(), row.getAction(),
                row.getTargetType(), row.getTargetId(), row.getDetail(), row.getCreatedAt());
    }
}
