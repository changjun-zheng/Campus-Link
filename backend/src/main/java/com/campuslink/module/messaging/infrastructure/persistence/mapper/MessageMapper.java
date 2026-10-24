package com.campuslink.module.messaging.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campuslink.module.messaging.infrastructure.persistence.MessageDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** messages 的 MyBatis-Plus Mapper（CR-135） */
@Mapper
public interface MessageMapper extends BaseMapper<MessageDO> {

    /**
     * 会话内消息分页：最新在前（倒序），id 作稳定第二键（同秒消息不抖动）；
     * 参数化 {@code LIMIT / OFFSET}，不拼字符串。
     */
    @Select("""
            SELECT m.id, m.conversation_id, m.sender_id, m.receiver_id, m.content, m.created_at, m.read_at
            FROM messages m
            WHERE m.conversation_id = #{conversationId}
            ORDER BY m.created_at DESC, m.id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<MessageDO> pageByConversation(@Param("conversationId") long conversationId,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM messages WHERE conversation_id = #{conversationId}")
    long countByConversation(@Param("conversationId") long conversationId);

    /** 未读总数：只数"接收人是我且未读"，发送者自己的未读不计入 */
    @Select("SELECT COUNT(*) FROM messages WHERE receiver_id = #{userId} AND read_at IS NULL")
    long countUnread(@Param("userId") long userId);
}
