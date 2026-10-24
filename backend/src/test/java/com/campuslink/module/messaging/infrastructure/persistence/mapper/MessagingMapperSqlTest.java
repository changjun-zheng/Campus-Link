package com.campuslink.module.messaging.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 私信 SQL 的口径断言（F-SOC-003 / CR-135）：读 {@code @Select} 注解文本
 * （Mockito 结构上捕不到 SQL 条件，与 CR-104 / CR-133 同法）。
 *
 * <p>四条口径：① 会话列表按登录者一侧过滤，且 total 与页内容同一 WHERE；
 * ② 摘要只取纯文本前 50 字、不取 HTML；③ 消息倒序、同秒以 id 稳定排序；
 * ④ 未读只数"接收人是我且 read_at IS NULL"。
 */
class MessagingMapperSqlTest {

    @Test
    @DisplayName("会话列表 SQL：登录者一侧过滤 + 倒序 + 分页参数化 + 未读子查询")
    void conversationPageSqlIsScopedAndParameterized() throws NoSuchMethodException {
        String sql = selectSql(ConversationMapper.class, "pageOfUser", long.class, int.class, int.class);
        assertThat(sql)
                .contains("FROM conversations c")
                .contains("(c.pair_low_id = #{userId} OR c.pair_high_id = #{userId})")
                .contains("AND c.last_message_id IS NOT NULL")
                .contains("um.receiver_id = #{userId}")
                .contains("um.read_at IS NULL")
                .contains("LEFT(m.content, 50)")
                .contains("ORDER BY c.last_message_at DESC, c.id DESC")
                .contains("LIMIT #{limit} OFFSET #{offset}")
                .doesNotContain("${");
    }

    @Test
    @DisplayName("会话计数 SQL：与分页同一 WHERE（total 同源）")
    void conversationCountSharesWhere() throws NoSuchMethodException {
        String sql = selectSql(ConversationMapper.class, "countOfUser", long.class);
        assertThat(sql)
                .contains("(c.pair_low_id = #{userId} OR c.pair_high_id = #{userId})")
                .contains("AND c.last_message_id IS NOT NULL")
                .doesNotContain("${");
    }

    @Test
    @DisplayName("消息分页 SQL：会话内过滤 + 最新在前 + 参数化分页 + 不拼字符串")
    void messagePageSqlIsScopedAndOrdered() throws NoSuchMethodException {
        String sql = selectSql(MessageMapper.class, "pageByConversation", long.class, int.class, int.class);
        assertThat(sql)
                .contains("FROM messages m")
                .contains("WHERE m.conversation_id = #{conversationId}")
                .contains("ORDER BY m.created_at DESC, m.id DESC")
                .contains("LIMIT #{limit} OFFSET #{offset}")
                .doesNotContain("${");
    }

    @Test
    @DisplayName("未读总数 SQL：只数接收人是我且未读的消息")
    void unreadCountSqlFiltersReceiverAndNullReadAt() throws NoSuchMethodException {
        String sql = selectSql(MessageMapper.class, "countUnread", long.class);
        assertThat(sql)
                .contains("FROM messages")
                .contains("receiver_id = #{userId}")
                .contains("read_at IS NULL")
                .doesNotContain("${");
    }

    @Test
    @DisplayName("会话总数 SQL：只数该会话全部消息（与分页条件同源）")
    void messageCountSqlFiltersConversation() throws NoSuchMethodException {
        assertThat(selectSql(MessageMapper.class, "countByConversation", long.class))
                .contains("FROM messages")
                .contains("conversation_id = #{conversationId}");
    }

    private static String selectSql(Class<?> mapper, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        return String.join("", mapper.getMethod(name, parameterTypes).getAnnotation(Select.class).value());
    }
}
