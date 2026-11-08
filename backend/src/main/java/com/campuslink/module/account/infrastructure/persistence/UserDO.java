package com.campuslink.module.account.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * users 表数据对象（MyBatis-Plus）。本类属于基础设施，不进入领域层——
 * 与聚合的互转经 {@link AccountConverter}（防腐层）。
 */
@Data
@TableName("users")
public class UserDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String emailEnc;
    private String emailHash;
    private String phoneEnc;
    private String phoneHash;
    private String nickname;
    private String avatarUrl;
    private String school;
    private String major;
    private String grade;
    private String bio;
    private String role;
    private String status;
    private String studentIdEnc;
    private String studentIdHash;
    private Instant mutedUntil;
    private Boolean verified;
    private Boolean anonymized;
    private Instant deleteAt;
    private Instant createdAt;
    private Instant updatedAt;
}
