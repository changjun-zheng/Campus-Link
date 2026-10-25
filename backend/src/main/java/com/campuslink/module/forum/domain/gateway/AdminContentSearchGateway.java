package com.campuslink.module.forum.domain.gateway;

import java.time.Instant;
import java.util.Collection;

/**
 * 运营后台内容搜索端口（F-ADMIN-002 / CR-141）：帖子与楼层的只读检索。
 *
 * <p>只列**未删**内容（{@code is_deleted=0}），{@code status} 不过滤——已下架内容仍要能被搜到并恢复；
 * 楼层额外排除父帖已删（父帖不可访问，处置无意义）。
 *
 * <p>{@code authorIds} 为 null 表示不筛作者；空集合由调用方短路，实现不接收空 {@code IN ()}。
 */
public interface AdminContentSearchGateway {

    PageResult<ContentRow> search(String kind, String keyword, Collection<Long> authorIds,
                                  Integer days, int page, int size);

    /**
     * 检索条目（窄载体，不装配完整聚合）：{@code title} 为帖子标题（楼层取父帖标题），
     * {@code contentMd} 交给应用层截成纯文本摘要，**web 层拿不到原文**。
     */
    record ContentRow(long id, String kind, String title, String contentMd, String boardName,
                      long authorId, String authorNickname, String status, Instant createdAt) {
    }
}
