package com.campuslink.module.forum.infrastructure.persistence;

import lombok.Data;

import java.time.Instant;

/**
 * 他人主页「公开楼层」查询行（CR-104）：{@code replies} JOIN {@code posts} 的**只读结果**，
 * 不对应单张表，故不加 {@code @TableName}（先例见 {@link MyReplyDO}）。
 *
 * <p>与 {@link MyReplyDO} 刻意分成两个 DO 而不是共用一份：本查询行**不取 {@code r.status} 列**——
 * 路人视角的 SQL 已在 WHERE 里把不可见楼层滤掉，剩下能返回的每一行 {@code status} 恒为 PUBLISHED，
 * 保留这一列只会让"这一列能不能下发"变成前端和 web 层的口头约定（{@code PublicReplyVo} 因此根本没有该字段）。
 *
 * <p>列名经 {@code map-underscore-to-camel-case} 自动映射；转领域载体见 {@code ReplyRepositoryImpl}。
 */
@Data
public class PublicReplyDO {

    private Long id;
    private Long postId;
    private String postTitle;
    private Integer floorNo;
    private String contentMd;
    private Instant createdAt;
}
