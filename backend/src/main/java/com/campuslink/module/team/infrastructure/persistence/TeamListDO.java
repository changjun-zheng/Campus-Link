package com.campuslink.module.team.infrastructure.persistence;

import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 广场列表联表查询行（CR-137）：{@code teams} + {@code users} + 已加入人数子查询的只读投影。
 */
@Data
public class TeamListDO {

    private Long id;
    private String title;
    private String direction;
    private String target;
    private Integer capacity;
    private Long acceptedCount;
    private String status;
    private LocalDate deadline;
    private Long ownerId;
    private String ownerNickname;
    private Instant createdAt;
}
