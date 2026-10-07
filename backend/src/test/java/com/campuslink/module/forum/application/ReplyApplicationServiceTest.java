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
                Reply.rehydrate(456L, POST_ID, AUTHOR_ID, 1, "hi", "<p>hi</p>", null, false, 0, Instant.now()));

        PublishedReply result = service.reply(POST_ID, AUTHOR_ID, new PublishReplyCommand("**hi**", null));

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

        assertThatThrownBy(() -> service.reply(POST_ID, AUTHOR_ID, new PublishReplyCommand("hi", null)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_FOUND));

        verify(postRepository, never()).incrementReplyCountAndGet(any());
        verifyNoInteractions(replyRepository, notificationService);
    }

    @Test
    @DisplayName("REMOVED 的帖子不可回帖 → 3001")
    void removedPostIsNotRepliable() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post(PostStatus.REMOVED)));

        assertThatThrownBy(() -> service.reply(POST_ID, AUTHOR_ID, new PublishReplyCommand("hi", null)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_FOUND));

        verifyNoInteractions(replyRepository);
    }

    @Test
    @DisplayName("带引用回帖（CR-085）：落库携带 quotedReplyId，帖子作者与被引用楼层作者各收一条通知")
    void replyWithQuoteSavesReferenceAndNotifiesBoth() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post(PostStatus.PUBLISHED)));
        when(replyRepository.findVisibleById(11L)).thenReturn(Optional.of(quoted(7L)));
        when(postRepository.incrementReplyCountAndGet(POST_ID)).thenReturn(2);
        when(replyRepository.save(any())).thenReturn(
                Reply.rehydrate(456L, POST_ID, AUTHOR_ID, 2, "hi", "<p>hi</p>", 11L, false, 0, Instant.now()));

        PublishedReply result = service.reply(POST_ID, AUTHOR_ID, new PublishReplyCommand("hi", 11L));

        assertThat(result.floorNo()).isEqualTo(2);
        ArgumentCaptor<Reply> captor = ArgumentCaptor.forClass(Reply.class);
        verify(replyRepository).save(captor.capture());
        assertThat(captor.getValue().getQuotedReplyId()).isEqualTo(11L);
        verify(notificationService).postReplied(AUTHOR_ID, 456L, 7L);
        verify(notificationService).replyQuoted(AUTHOR_ID, 456L, 7L);
    }

    @Test
    @DisplayName("引用目标不可见（不存在 / 已下架 / 已删除）→ 3001，且不分配楼层、不落库、不发通知")
    void invisibleQuotedReplyIsNotFound() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post(PostStatus.PUBLISHED)));
        when(replyRepository.findVisibleById(11L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reply(POST_ID, AUTHOR_ID, new PublishReplyCommand("hi", 11L)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_FOUND));

        verify(postRepository, never()).incrementReplyCountAndGet(any());
        verify(replyRepository, never()).save(any());
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("引用他帖楼层 → 同一个 3001（不区分原因，防按 id 探测他帖内容），零副作用")
    void crossPostQuotedReplyIsNotFound() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post(PostStatus.PUBLISHED)));
        when(replyRepository.findVisibleById(11L)).thenReturn(Optional.of(
                Reply.rehydrate(11L, 8L, 7L, 1, "内容", "<p>内容</p>", null, false, 0, Instant.now())));

        assertThatThrownBy(() -> service.reply(POST_ID, AUTHOR_ID, new PublishReplyCommand("hi", 11L)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_FOUND));

        verify(postRepository, never()).incrementReplyCountAndGet(any());
        verify(replyRepository, never()).save(any());
        verifyNoInteractions(notificationService);
    }

    /**
     * 楼层号是 {@code reply_count} 计数产物、机审是 fail-closed 安全门——引用校验必须排在两者之前，
     * 否则"引用目标不存在"这种确定性错误会被 5002 / 楼层号消耗抢在前面，语义就错了。
     */
    @Test
    @DisplayName("引用校验先于机审：引用目标缺失时 3001 抢在 5002 之前给出，机审从未被触达")
    void quoteCheckRunsBeforeModeration() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post(PostStatus.PUBLISHED)));
        when(replyRepository.findVisibleById(11L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reply(POST_ID, AUTHOR_ID, new PublishReplyCommand("hi", 11L)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_FOUND));

        verify(moderationGateway, never()).moderate(any());
        verify(postRepository, never()).incrementReplyCountAndGet(any());
        verifyNoInteractions(notificationService);
    }

    private static Reply quoted(Long authorId) {
        return Reply.rehydrate(11L, POST_ID, authorId, 1, "被引内容", "<p>被引内容</p>", null, false, 0, Instant.now());
    }

    private static Post post(PostStatus status) {
        return Post.rehydrate(POST_ID, 1L, 7L, BoardType.QUESTION, "标题", "正文", "<p>正文</p>",
                status, 0, 0, false, null, null, null);
    }
}
