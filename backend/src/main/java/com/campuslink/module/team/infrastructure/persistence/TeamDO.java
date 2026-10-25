package com.campuslink.module.team.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

/** teams 表数据对象（CR-137）。本类属基础设施，不进入领域层。 */
@Data
@TableName("teams")
public class TeamDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ownerId;
    private String title;
    private String descriptionMd;
    private String direction;
    private String target;
    private Integer capacity;
    /** AES-GCM 密文；仓储适配器在读写两侧加解密，本字段永不出现在 web 出参 */
    private String contactEnc;
    private String status;
    private LocalDate deadline;
    private Instant createdAt;
    private Instant updatedAt;
}
