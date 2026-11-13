package com.campuslink.integration;

import com.campuslink.module.forum.application.ForumQueryApplicationService;
import com.campuslink.module.forum.application.PostApplicationService;
import com.campuslink.module.forum.application.ReplyApplicationService;
import com.campuslink.module.forum.application.cmd.ForumResults.ReplyItem;
import com.campuslink.module.forum.application.cmd.PublishPostCommand;
import com.campuslink.module.forum.application.cmd.PublishReplyCommand;
import com.campuslink.module.forum.domain.gateway.PageResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通知跳转楼层跨页定位的真实数据库集成测试（CR-220）。
 *
 * <p>单测验证的是"页码怎么用"，验证不到真正危险的部分：{@code countVisibleUpTo} 的过滤与排序
 * 是否和楼层列表**逐字同源**。排序口径（最佳答案置顶、其余 floor_no 升序）与可见性过滤一旦漂移，
 * 算出的页码就会和真实列表组成不一致、把用户送到错误页。本类在测试事务内执行，结束整体回滚。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "SPRING_IT", matches = "true")
@Transactional
class ReplyFocusPageIntegrationTest {

    @Autowired
    private ForumQueryApplicationService queries;
    @Autowired
    private PostApplicationService posts;
    @Autowired
    private ReplyApplicationService replies;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void focusOnTwentySecondReplyLandsOnSecondPage() {
        long askerId = userIdAt(0);
        long replierId = userIdAt(1);
        long postId = publish("focus page probe", askerId);
        List<Long> replyIds = replyTimes(postId, replierId, 25);

        PageResult<ReplyItem> page = queries.listReplies(postId, 1, 20, null, replyIds.get(21));

        assertThat(page.page()).isEqualTo(2);
        assertThat(page.items()).extracting(ReplyItem::id).contains(replyIds.get(21));
    }

    @Test
    void acceptedReplyIsPinnedSoItsFocusPageIsFirst() {
        long askerId = userIdAt(0);
        long replierId = userIdAt(1);
        long postId = publish("focus accepted probe", askerId);
        List<Long> replyIds = replyTimes(postId, replierId, 25);
        posts.acceptReply(askerId, postId, replyIds.get(21));

        PageResult<ReplyItem> page = queries.listReplies(postId, 1, 20, null, replyIds.get(21));

        // 第 22 条被采纳后置顶，列表第一项就是它 ⇒ 定位必须落在第 1 页，而不是按 floor_no 算出的第 2 页
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.items().getFirst().id()).isEqualTo(replyIds.get(21));
    }

    private long publish(String title, long authorId) {
        return posts.publish(authorId, new PublishPostCommand("qna",
                title + " " + UUID.randomUUID().toString().substring(0, 8), "integration probe body",
                List.of(), false)).id();
    }

    private List<Long> replyTimes(long postId, long authorId, int count) {
        List<Long> ids = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            ids.add(replies.reply(postId, authorId, new PublishReplyCommand("focus probe reply " + i, null)).id());
        }
        return ids;
    }

    private long userIdAt(int offset) {
        List<Long> ids = jdbc.queryForList("SELECT id FROM users ORDER BY id", Long.class);
        if (ids.size() <= offset) {
            throw new IllegalStateException("集成测试需要至少 " + (offset + 1) + " 个用户，实际 " + ids.size());
        }
        return ids.get(offset);
    }
}
