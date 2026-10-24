package com.campuslink.module.account.domain.gateway;

import java.util.List;
import java.util.Set;

/**
 * 拉黑关系端口（F-SOC-004 / CR-129）：单向关系，读侧以 {@link #blockedIdsOf} 一次取集合、
 * 避免列表 / 搜索逐条查询（与关注 Feed 的批量口径一致）。
 */
public interface UserBlockRepository {

    /** 拉黑；重复拉黑幂等。返回是否真的新插入（false 表示已存在），应用层据此决定是否记审计 */
    boolean block(long blockerId, long blockedId);

    /** 解除；不存在也幂等（不报错） */
    void unblock(long blockerId, long blockedId);

    boolean exists(long blockerId, long blockedId);

    /** 拉黑者的全部 blockedId（读侧过滤用，调用方一次取用） */
    Set<Long> blockedIdsOf(long blockerId);

    /** 黑名单分页（按拉黑时间倒序），返回 blockedId */
    List<Long> pageBlockedIds(long blockerId, int offset, int limit);

    long countBlocked(long blockerId);
}
