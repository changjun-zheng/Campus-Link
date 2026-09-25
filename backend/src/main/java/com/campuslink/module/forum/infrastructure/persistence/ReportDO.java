package com.campuslink.module.forum.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/** reports 表数据对象（V1 已建；举报工单，F-SAFE-002 / CR-082） */
@Data
@TableName("reports")
public class ReportDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reporterId;
    private String targetType;
    private Long targetId;
    private String reason;
    private String detail;
    private String status;
    private String resolution;
    private Long handlerId;
    private Instant createdAt;
    private Instant closedAt;
}
