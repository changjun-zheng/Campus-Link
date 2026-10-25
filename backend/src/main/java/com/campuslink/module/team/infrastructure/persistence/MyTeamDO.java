package com.campuslink.module.team.infrastructure.persistence;

import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

/** 「我的组队」联表查询行（CR-137）：组队字段 + 我与它的关系 + 发起人视角的待审批数 */
@Data
public class MyTeamDO {

    private Long id;
    private String title;
    private String direction;
    private String target;
    private Integer capacity;
    private Long acceptedCount;
    private String status;
    private LocalDate deadline;
    private String myRole;
    private String myStatus;
    private Long pendingCount;
    private Instant createdAt;
}
