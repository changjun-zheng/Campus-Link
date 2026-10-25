package com.campuslink.module.forum.infrastructure.persistence;

import lombok.Data;

import java.time.Instant;

/**
 * 后台内容搜索查询行（F-ADMIN-002 / CR-141）：{@code posts} / {@code replies} 联表只读投影。
 * 列名经 {@code map-underscore-to-camel-case} 映射到同名属性。
 */
@Data
public class AdminContentRowDO {

    private Long id;
    private String kind;
    private String title;
    private String contentMd;
    private String boardName;
    private Long authorId;
    private String authorNickname;
    private String status;
    private Instant createdAt;
}
