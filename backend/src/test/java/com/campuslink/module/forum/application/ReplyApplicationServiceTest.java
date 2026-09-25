package com.campuslink.module.forum.application;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.markdown.MarkdownRenderer;
import static org.mockito.Mockito.mock;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.forum.application.cmd.ForumResults.PublishedReply;
import com.campuslink.module.forum.application.cmd.PublishReplyCommand;
import com.campuslink.module.forum.domain.gateway.PostRepository;
import com.campuslink.module.forum.domain.gateway.ReplyRepository;
import com.campuslink.module.forum.domain.model.BoardType;
import com.campuslink.module.forum.domain.model.Post;
import com.campuslink.module.forum.domain.model.PostStatus;
import com.campuslink.module.forum.domain.model.Reply;
import com.campuslink.module.notification.application.NotificationApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 回帖用例：楼层号必须取自 {@code reply_count} 自增后的值（设计 §4.1，本 Sprint 唯一并发点），
 * 且帖子不可读时**不得**分配楼层。
 */
@ExtendWith(MockitoExtension.class)
class ReplyApplicationServiceTest {

    private static final long POST_ID = 9L;
    private static final long AUTHOR_ID = 42L;

    @Mock
    private PostRepository postRepository;
    @Mock
    private ReplyRepository replyRepository;
    @Mock
    private NotificationApplicationService notificationService;
    @Mock
    private com.campuslink.module.forum.domain.gateway.ModerationGateway moderationGateway;
    @Mock
    private com.campuslink.module.account.application.AccountApplicationService accountApplicationService;

    private ReplyApplicationService service;

    @BeforeEach
    void setUp() {
        // lenient：missingPost / removedPost 两条用例在帖子可见性校验即失败，不会走到机审（严格模式下全局桩需放行）
        lenient().when(moderationGateway.moderate(any())).thenReturn(com.campuslink.module.forum.domain.gateway.ModerationGateway.ModerationVerdict.ok());
        service = new ReplyApplicationService(postRepository, replyRepository, new MarkdownRenderer(),
                notificationService, moderationGateway, accountApplicationService);
    }

    @Test
    @DisplayName("楼层号 = reply_count 自增后的值；首条回复为 1 楼")
    void floorNoComesFromIncrementedReplyCount() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post(PostStatus.PUBLISHED)));
        when(postRepository.incrementReplyCountAndGet(POST_ID)).thenReturn(1);
        when(replyRepository.save(any())).thenReturn(
                Reply.rehydrate(456L, POST_ID, AUTHOR_ID, 1, "hi", "<p>hi</p>", false, 0, Instant.now()));

        PublishedReply result = service.reply(POST_ID, AUTHOR_ID, new PublishReplyCommand("**hi**"));

        assertThat(result.id()).isEqualTo(456L);
        assertThat(result.floorNo()).isEqualTo(1);
        ArgumentCaptor<Reply> captor = ArgumentCaptor.forClass(Reply.class);
        verify(replyRepository).save(captor.capture());
        Reply saved = captor.getValue();
        assertThat(saved.getFloorNo()).isEqualTo(1);
        assertThat(saved.getPostId()).isEqualTo(POST_ID);
        assertThat(saved.getAuthorId()).isEqualTo(AUTHOR_ID);
        assertThat(saved.getContentHtml()).contains("<strong>hi</strong>");
        // 回帖成功后同事务发通知给帖子作者（F-SOC-001）
        verify(notificationService).postReplied(AUTHOR_ID, 456L, 7L);
    }

    @Test
    @DisplayName("帖子不存在 → 3001，且不分配楼层、不落库、不发通知")
    void missingPostIsNotFound() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reply(POST_ID, AUTHOR_ID, new PublishReplyCommand("hi")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_FOUND));

        verify(postRepository, never()).incrementReplyCountAndGet(any());
        verifyNoInteractions(replyRepository, notificationService);
    }

    @Test
    @DisplayName("REMOVED 的帖子不可回帖 → 3001")
    void removedPostIsNotRepliable() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post(PostStatus.REMOVED)));

        assertThatThrownBy(() -> service.reply(POST_ID, AUTHOR_ID, new PublishReplyCommand("hi")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_FOUND));

        verifyNoInteractions(replyRepository);
    }

    private static Post post(PostStatus status) {
        return Post.rehydrate(POST_ID, 1L, 7L, BoardType.QUESTION, "标题", "正文", "<p>正文</p>",
                status, 0, 0, false, null, null, null);
    }
}
