package com.campuslink.module.account.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** user_blocks 行（F-SOC-004 / CR-129）：联合主键，无单 id */
@Data
@NoArgsConstructor
@TableName("user_blocks")
public class UserBlockDO {

    private Long blockerId;
    private Long blockedId;
    private Instant createdAt;
}
