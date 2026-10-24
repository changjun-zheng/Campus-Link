package com.campuslink.module.forum.infrastructure.persistence;

import lombok.Data;

import java.time.Instant;

/**
 * 巡查队列查询行（F-SAFE-004 / CR-133）：{@code posts} / {@code replies} 联表只读投影。
 * 列名经 {@code map-underscore-to-camel-case} 映射到同名属性。
 */
@Data
public class ReviewItemRow {

    private Long id;
    private String kind;
    private String title;
    private String boardName;
    private String authorNickname;
    private String status;
    private Instant createdAt;
}
