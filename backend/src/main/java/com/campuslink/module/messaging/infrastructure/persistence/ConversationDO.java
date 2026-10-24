package com.campuslink.module.messaging.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/** conversations 表数据对象（CR-135）。本类属基础设施，不进入领域层。 */
@Data
@TableName("conversations")
public class ConversationDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long pairLowId;
    private Long pairHighId;
    private Long lastMessageId;
    private Instant lastMessageAt;
    private Instant createdAt;
    private Instant updatedAt;
}
