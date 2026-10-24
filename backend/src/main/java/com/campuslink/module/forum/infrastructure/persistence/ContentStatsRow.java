package com.campuslink.module.forum.infrastructure.persistence;

import lombok.Data;

/**
 * 运营看板「内容组」只读查询行（F-ADMIN-005 / CR-131）：{@code posts} / {@code replies} 共用的聚合结果。
 * 不对应单张业务表映射，故不加 {@code @TableName}（先例见 {@code PublicReplyDO}）。
 */
@Data
public class ContentStatsRow {

    private Long total;
    private Long published;
    private Long removed;
    private Long todayCreated;
}
