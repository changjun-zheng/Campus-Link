package com.campuslink.module.forum.domain.gateway;

import java.util.Collection;
import java.util.List;

/**
 * 搜索引擎读端口（CR-181 / ADR-004）：与 {@link PostRepository#search} 同参数、同语义的检索入口，
 * 由基础设施适配器落到 MeiliSearch。
 *
 * <p>只返回**有序 id + 估算总数**：完整帖子仍由 MySQL 读取并做最终可见性校验（纵深防御），
 * 这样索引同步滞后也不会把已删除 / 已下架内容漏给读者。
 */
public interface PostSearchGateway {

    SearchHits search(String keyword, Long boardId, Integer days, Collection<Long> excludedAuthorIds,
                      Collection<Long> excludedPostIds, int page, int size);

    /** {@code estimatedTotal} 为搜索引擎的估算值（MeiliSearch 的 estimatedTotalHits），不是精确 count */
    record SearchHits(List<Long> ids, long estimatedTotal) {
    }
}
