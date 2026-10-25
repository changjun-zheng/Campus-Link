package com.campuslink.module.forum.infrastructure.persistence;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campuslink.module.forum.domain.gateway.AdminContentSearchGateway;
import com.campuslink.module.forum.domain.gateway.PageResult;
import com.campuslink.module.forum.infrastructure.persistence.mapper.AdminContentSearchMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;

/** 适配器：AdminContentSearchGateway 的 MyBatis-Plus 实现（F-ADMIN-002 / CR-141） */
@Repository
@RequiredArgsConstructor
public class AdminContentSearchGatewayImpl implements AdminContentSearchGateway {

    private final AdminContentSearchMapper adminContentSearchMapper;

    @Override
    public PageResult<ContentRow> search(String kind, String keyword, Collection<Long> authorIds,
                                        Integer days, int page, int size) {
        IPage<AdminContentRowDO> query = new Page<>(page, size);
        IPage<AdminContentRowDO> result = "reply".equals(kind)
                ? adminContentSearchMapper.searchReplies(query, keyword, authorIds, days)
                : adminContentSearchMapper.searchPosts(query, keyword, authorIds, days);
        return new PageResult<>(result.getRecords().stream().map(AdminContentSearchGatewayImpl::toRow).toList(),
                result.getTotal(), page, size);
    }

    private static ContentRow toRow(AdminContentRowDO row) {
        return new ContentRow(row.getId(), row.getKind(), row.getTitle(), row.getContentMd(),
                row.getBoardName(), row.getAuthorId(), row.getAuthorNickname(), row.getStatus(),
                row.getCreatedAt());
    }
}
