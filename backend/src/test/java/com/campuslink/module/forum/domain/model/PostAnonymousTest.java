package com.campuslink.module.forum.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 匿名发帖的领域语义（F-FORUM-010 / CR-139）：匿名只是公开展示开关，真实 authorId 始终保留；
 * 编辑与采纳不得把匿名标记洗掉——否则作者改一次标题就"实名"了。
 */
class PostAnonymousTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-25T08:00:00Z");

    @Test
    @DisplayName("发布匿名帖：anonymous=true 且 authorId 落真实作者，计数值零")
    void publishKeepsRealAuthorForAnonymousPost() {
        Post post = Post.publish(1L, 42L, true, BoardType.DISCUSSION, "标题", "正文", "<p>正文</p>", null,
                List.of("Java"));

        assertThat(post.isAnonymous()).isTrue();
        assertThat(post.getAuthorId()).isEqualTo(42L);
        assertThat(post.getReplyCount()).isZero();
    }

    @Test
    @DisplayName("编辑普通 / 匿名帖都不改匿名标记（发布后不可切换）")
    void reviseKeepsAnonymousFlag() {
        Post plain = Post.publish(1L, 42L, false, BoardType.DISCUSSION, "标题", "正文", "<p>正文</p>", null,
                List.of());
        Post anonymous = Post.publish(1L, 42L, true, BoardType.DISCUSSION, "标题", "正文", "<p>正文</p>", null,
                List.of());

        assertThat(plain.revise("新标题", "新正文", "<p>新正文</p>", null, List.of()).isAnonymous()).isFalse();
        assertThat(anonymous.revise("新标题", "新正文", "<p>新正文</p>", null, List.of()).isAnonymous()).isTrue();
    }

    @Test
    @DisplayName("采纳最佳答案不洗掉匿名标记；仓储重建沿用列值")
    void acceptAndRehydrateKeepAnonymousFlag() {
        Post anonymous = Post.publish(1L, 42L, true, BoardType.QUESTION, "标题", "正文", "<p>正文</p>", null,
                List.of());

        assertThat(anonymous.acceptReply(9L, 99L).isAnonymous()).isTrue();
        assertThat(Post.rehydrate(7L, 1L, 42L, true, BoardType.DISCUSSION, "标题", "正文", "<p>正文</p>",
                PostStatus.PUBLISHED, 0, 0, 0, null, List.of(), false, null, CREATED_AT, CREATED_AT)
                .isAnonymous()).isTrue();
    }
}
