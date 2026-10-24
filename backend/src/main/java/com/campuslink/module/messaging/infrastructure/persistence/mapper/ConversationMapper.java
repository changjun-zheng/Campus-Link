package com.campuslink.module.messaging.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campuslink.module.messaging.infrastructure.persistence.ConversationDO;
import com.campuslink.module.messaging.infrastructure.persistence.ConversationSummaryDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** conversations 的 MyBatis-Plus Mapper（CR-135） */
@Mapper
public interface ConversationMapper extends BaseMapper<ConversationDO> {

    /**
     * 我的会话分页（CR-135）：对方 id / 昵称 / 头像、最后一条消息前 50 字、该会话未读数**同一条 SQL** 取齐
     * （列表不逐条查账号或消息；{@link #countOfUser} 复用同一 WHERE，total 与页内容同源）。
     *
     * <p>已注销账号的昵称回落「已注销用户」而不是从列表剔除——历史消息仍可读（方案 D2），
     * 把会话从列表藏掉等于让用户找不到自己的聊天记录。
     *
     * <p>参数化 {@code LIMIT / OFFSET}，不拼字符串；排序带 id 作稳定第二键。
     */
    @Select("""
            SELECT c.id AS conversation_id,
                   CASE WHEN c.pair_low_id = #{userId} THEN c.pair_high_id ELSE c.pair_low_id END AS peer_id,
                   CASE WHEN u.status = 'DEACTIVATED' THEN '已注销用户' ELSE u.nickname END AS peer_nickname,
                   u.avatar_url AS peer_avatar_url,
                   LEFT(m.content, 50) AS last_message_preview,
                   c.last_message_at AS last_message_at,
                   (SELECT COUNT(*)
                    FROM messages um
                    WHERE um.conversation_id = c.id
                      AND um.receiver_id = #{userId}
                      AND um.read_at IS NULL) AS unread_count
            FROM conversations c
            JOIN users u ON u.id = CASE WHEN c.pair_low_id = #{userId} THEN c.pair_high_id ELSE c.pair_low_id END
            LEFT JOIN messages m ON m.id = c.last_message_id
            WHERE (c.pair_low_id = #{userId} OR c.pair_high_id = #{userId})
              AND c.last_message_id IS NOT NULL
            ORDER BY c.last_message_at DESC, c.id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<ConversationSummaryDO> pageOfUser(@Param("userId") long userId,
                                           @Param("offset") int offset,
                                           @Param("limit") int limit);

    /** 我的会话总数：与 {@link #pageOfUser} 同一 WHERE（last_message_id IS NOT NULL 的会话才有列表意义） */
    @Select("""
            SELECT COUNT(*)
            FROM conversations c
            WHERE (c.pair_low_id = #{userId} OR c.pair_high_id = #{userId})
              AND c.last_message_id IS NOT NULL
            """)
    long countOfUser(@Param("userId") long userId);
}
