package com.campuslink.module.messaging.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/** messages 表数据对象（CR-135）。本类属基础设施，不进入领域层。 */
@Data
@TableName("messages")
public class MessageDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;
    private Long senderId;
    private Long receiverId;
    private String content;
    private Instant createdAt;
    private Instant readAt;
}
