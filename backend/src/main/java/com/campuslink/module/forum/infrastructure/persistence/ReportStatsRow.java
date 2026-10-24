package com.campuslink.module.forum.infrastructure.persistence;

import lombok.Data;

/** 运营看板「举报组」只读查询行（F-ADMIN-005 / CR-131）：{@code reports} 的聚合结果。 */
@Data
public class ReportStatsRow {

    private Long total;
    private Long pending;
    private Long closed;
}
