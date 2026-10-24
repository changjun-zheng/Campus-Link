package com.campuslink.module.account.infrastructure.persistence;

import lombok.Data;

/**
 * {@code users} 按状态分组的只读查询行（F-ADMIN-005 / CR-131）。
 *
 * <p>不对应单张业务表映射，故不加 {@code @TableName}（先例见 {@code PublicReplyDO}）。
 * 列名 {@code cnt} 经 {@code map-underscore-to-camel-case} 自动映射到同名属性。
 */
@Data
public class StatusCountRow {

    private String status;
    private Long cnt;
}
