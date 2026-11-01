package com.campuslink.module.forum.domain.gateway;

import com.campuslink.module.forum.domain.model.SearchSyncCursor;

import java.util.Optional;

/**
 * 索引同步游标的持久化端口（CR-181）。开发期由 Redis 适配；丢失时返回空，调度器改走全量重建。
 */
public interface SearchSyncStateGateway {

    Optional<SearchSyncCursor> read();

    void write(SearchSyncCursor cursor);

    void clear();
}
