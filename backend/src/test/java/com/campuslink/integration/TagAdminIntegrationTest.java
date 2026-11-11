package com.campuslink.integration;

import com.campuslink.module.forum.application.PostApplicationService;
import com.campuslink.module.forum.application.cmd.PublishPostCommand;
import com.campuslink.module.forum.domain.gateway.TagRepository;
import com.campuslink.module.forum.domain.gateway.TagRepository.TagSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 管理端标签重命名 / 合并的真实数据库集成测试（CR-217 补强）。
 *
 * <p>单测里的适配器是用 mock 拼的，验证不到真正危险的部分：{@code tags} / {@code post_tags} /
 * {@code posts.tags} 三处是否在 MySQL 上同步、外键 {@code fk_pt_tag} 是否被正确解开、
 * {@code use_count} 的加法是否与"累计挂载次数"口径一致。本类每条用例都在测试事务内执行，
 * 结束时整体回滚，不污染开发库。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "SPRING_IT", matches = "true")
@Transactional
class TagAdminIntegrationTest {

    @Autowired
    private TagRepository tagRepository;
    @Autowired
    private PostApplicationService posts;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void renameKeepsLinkAndRewritesRedundantColumn() {
        String oldName = unique("RN");
        String newName = oldName + "X";
        long postId = publish("tag rename probe", List.of(oldName));
        TagSummary tag = requireTag(oldName);
        int useCountBefore = tag.useCount();

        assertThat(tagRepository.renameTag(tag.id(), newName)).isEqualTo(1);

        assertThat(jdbc.queryForObject("SELECT name FROM tags WHERE id=?", String.class, tag.id()))
                .isEqualTo(newName);
        assertThat(linkCount(postId, tag.id())).isEqualTo(1);
        assertThat(redundantTags(postId)).isEqualTo(newName);
        assertThat(jdbc.queryForObject("SELECT use_count FROM tags WHERE id=?", Integer.class, tag.id()))
                .isEqualTo(useCountBefore);
    }

    @Test
    void renameOntoExistingNameHitsUniqueKeyAndLeavesRowUntouched() {
        String kept = unique("RA");
        String taken = unique("RB");
        publish("tag rename collision probe", List.of(kept));
        publish("tag rename collision probe 2", List.of(taken));
        TagSummary tag = requireTag(kept);
        requireTag(taken);

        assertThatThrownBy(() -> tagRepository.renameTag(tag.id(), taken))
                .isInstanceOf(DuplicateKeyException.class);

        assertThat(jdbc.queryForObject("SELECT name FROM tags WHERE id=?", String.class, tag.id()))
                .isEqualTo(kept);
    }

    @Test
    void mergeMovesLinksAndAddsAccumulatedUseCount() {
        String sourceName = unique("MS");
        String targetName = unique("MD");
        long sourcePost = publish("tag merge source probe", List.of(sourceName));
        long targetPost = publish("tag merge target probe", List.of(targetName));
        TagSummary source = requireTag(sourceName);
        TagSummary target = requireTag(targetName);
        int expectedUseCount = source.useCount() + target.useCount();

        assertThat(tagRepository.mergeTag(source.id(), target.id())).isEqualTo(1);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tags WHERE id=?", Long.class, source.id()))
                .isZero();
        assertThat(linkCount(sourcePost, target.id())).isEqualTo(1);
        assertThat(redundantTags(sourcePost)).isEqualTo(targetName);
        assertThat(redundantTags(targetPost)).isEqualTo(targetName);
        assertThat(jdbc.queryForObject("SELECT use_count FROM tags WHERE id=?", Integer.class, target.id()))
                .isEqualTo(expectedUseCount);
    }

    @Test
    void mergeDropsDuplicateLinkWhenPostCarriesBothTags() {
        String sourceName = unique("DS");
        String targetName = unique("DD");
        long postId = publish("tag merge duplicate probe", List.of(sourceName, targetName));
        TagSummary source = requireTag(sourceName);
        TagSummary target = requireTag(targetName);

        assertThat(tagRepository.mergeTag(source.id(), target.id())).isEqualTo(1);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_tags WHERE post_id=?", Long.class, postId))
                .isEqualTo(1);
        assertThat(linkCount(postId, target.id())).isEqualTo(1);
        assertThat(redundantTags(postId)).isEqualTo(targetName);
    }

    /** 发一条带标签的真实帖子，返回帖子 id（走真实发帖链路，三处写入与线上同一条路径） */
    private long publish(String title, List<String> tags) {
        long authorId = jdbc.queryForObject("SELECT id FROM users ORDER BY id LIMIT 1", Long.class);
        return posts.publish(authorId,
                new PublishPostCommand("qna", title + " " + unique("T"), "integration probe body", tags, false))
                .id();
    }

    private TagSummary requireTag(String name) {
        return tagRepository.findTagByName(name).orElseThrow();
    }

    private long linkCount(long postId, long tagId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM post_tags WHERE post_id=? AND tag_id=?",
                Long.class, postId, tagId);
    }

    private String redundantTags(long postId) {
        return jdbc.queryForObject("SELECT tags FROM posts WHERE id=?", String.class, postId);
    }

    /** 每轮唯一标签名（≤32 字），避免与库内既有数据或并行用例撞名 */
    private static String unique(String prefix) {
        return "IT218" + prefix + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }
}
