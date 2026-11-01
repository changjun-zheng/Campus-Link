package com.campuslink.module.forum.domain.gateway;

import com.campuslink.module.forum.domain.model.PostSearchDocument;

import java.util.Collection;

/**
 * 搜索索引写端口（CR-181）：建索引 / 设置、批量 upsert、删除、清空与文档计数。
 *
 * <p>所有写操作在适配器内等待索引任务完成再返回——调用方（增量同步）只有拿到成功回执才推进游标，
 * 失败时下一轮会重放同一批（upsert 幂等）。
 */
public interface PostSearchIndexGateway {

    /** 幂等：索引不存在则创建，并确保 searchable / filterable / sortable / ranking 设置就位 */
    void ensureIndex();

    /** 当前索引文档数；索引不存在时返回 0 */
    long documentCount();

    /** 清空全部文档（保留索引与设置），全量重建前调用 */
    void clear();

    void upsert(Collection<PostSearchDocument> documents);

    void deleteByIds(Collection<Long> ids);
}
