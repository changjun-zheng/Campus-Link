package com.campuslink.module.forum.infrastructure.persistence;

import lombok.Data;

import java.time.Instant;

/**
 * 管理端标签查询行（F-ADMIN-004 标签侧 / CR-132）：{@code tags} 表的只读投影。
 * 列名经 {@code map-underscore-to-camel-case} 映射到同名属性；{@code created_at} 为 UTC 墙钟。
 */
@Data
public class AdminTagRow {

    private Long id;
    private String name;
    private Integer useCount;
    private Instant createdAt;
}
