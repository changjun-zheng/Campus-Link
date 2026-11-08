package com.campuslink.module.notification.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/** notifications 表数据对象（MyBatis-Plus）。本类属基础设施，不进入领域层。 */
@Data
@TableName("notifications")
public class NotificationDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String type;
    private Long actorId;
    private String targetType;
    private Long targetId;
    private String message;
    private Boolean isRead;
    private Instant createdAt;
}
