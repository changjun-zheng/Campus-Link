package com.campuslink.module.forum.application;

import com.campuslink.common.audit.AuditService;
import com.campuslink.common.exception.ApiException;
import com.campuslink.common.markdown.MarkdownRenderer;
import static org.mockito.Mockito.mock;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.forum.application.cmd.ForumResults.PublishedPost;
import com.campuslink.module.forum.application.cmd.PublishPostCommand;
import com.campuslink.module.forum.application.cmd.UpdatePostCommand;
import com.campuslink.module.forum.domain.gateway.BoardRepository;
import com.campuslink.module.forum.domain.gateway.PostRepository;
import com.campuslink.module.forum.domain.gateway.ReplyRepository;
import com.campuslink.module.forum.domain.model.Board;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 发帖 / 采纳 / 删除三条写用例。发帖守的是渲染时机（发布时落库，ADR-005）、type 随版块、未知版块按资源不存在处理；
 * 采纳与删除守的是**归属授权与零副作用**——被拒时必须一条写操作都不落地（含审计），
 * 否则"403 了但数据变了"这种错法只能靠真机发现。
 */
@ExtendWith(MockitoExtension.class)
class PostApplicationServiceTest {

    @Mock
    private BoardRepository boardRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private ReplyRepository replyRepository;
    @Mock
    private NotificationApplicationService notificationService;
    @Mock
    private AuditService auditService;
    @Mock
    private com.campuslink.module.forum.domain.gateway.ModerationGateway moderationGateway;
    @Mock
    private com.campuslink.module.account.application.AccountApplicationService accountApplicationService;
    @Mock
    private com.campuslink.module.forum.domain.gateway.TagRepository tagRepository;

    private PostApplicationService service;

    @BeforeEach
    void setUp() {
        // MarkdownRenderer 是纯组件（无 I/O），直接 new，不必走 Spring 上下文
        service = new PostApplicationService(boardRepository, postRepository, replyRepository,
                new MarkdownRenderer(), notificationService, auditService, moderationGateway,
                accountApplicationService, tagRepository);
    }

    @Test
    @DisplayName("发帖：contentHtml 在发布时渲染落库，type 随版块，标题去首尾空白")
    void publishRendersHtmlAndSavesPost() {
        when(boardRepository.findByCode("qna"))
                .thenReturn(Optional.of(board(1L, "qna", BoardType.QUESTION)));
        when(moderationGateway.moderate(any()))
                .thenReturn(com.campuslink.module.forum.domain.gateway.ModerationGateway.ModerationVerdict.ok());
        when(postRepository.save(any())).thenAnswer(invocation -> {
            Post saved = invocation.getArgument(0);
            return Post.rehydrate(123L, saved.getBoardId(), saved.getAuthorId(), saved.getType(),
                    saved.getTitle(), saved.getContentMd(), saved.getContentHtml(), saved.getStatus(),
                    saved.getReplyCount(), saved.getLikeCount(), saved.isAccepted(),
                    saved.getAcceptedReplyId(), null, null);
        });

        PublishedPost result = service.publish(42L,
                new PublishPostCommand("qna", "  标题  ", "**加粗**", null, false));

        assertThat(result.id()).isEqualTo(123L);
        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        Post published = captor.getValue();
        assertThat(published.getTitle()).isEqualTo("标题");
        assertThat(published.getContentMd()).isEqualTo("**加粗**", null);
        assertThat(published.getContentHtml()).contains("<strong>加粗</strong>");
        assertThat(published.getType()).isEqualTo(BoardType.QUESTION);
        assertThat(published.getAuthorId()).isEqualTo(42L);
        assertThat(published.isVisible()).isTrue();
        assertThat(published.isAnonymous()).isFalse();
    }

    @Test
    @DisplayName("匿名发布：anonymous=true 透传到聚合（公开展示开关，真实作者仍落 author_id）")
    void anonymousFlagReachesAggregate() {
        when(boardRepository.findByCode("qna"))
                .thenReturn(Optional.of(board(1L, "qna", BoardType.QUESTION)));
        when(moderationGateway.moderate(any()))
                .thenReturn(com.campuslink.module.forum.domain.gateway.ModerationGateway.ModerationVerdict.ok());
        when(postRepository.save(any())).thenAnswer(invocation -> {
            Post saved = invocation.getArgument(0);
            return Post.rehydrate(123L, saved.getBoardId(), saved.getAuthorId(), saved.isAnonymous(),
                    saved.getType(), saved.getTitle(), saved.getContentMd(), saved.getContentHtml(), saved.getStatus(),
                    saved.getReplyCount(), saved.getLikeCount(), saved.getViewCount(), saved.getCoverUrl(),
                    saved.getTags(), saved.isAccepted(), saved.getAcceptedReplyId(), null, null);
        });

        service.publish(42L, new PublishPostCommand("qna", "匿名标题", "匿名正文", null, true));

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        assertThat(captor.getValue().isAnonymous()).isTrue();
        assertThat(captor.getValue().getAuthorId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("发帖入参：JSON 不带 anonymous 字段可正常反序列化（契约：非 required；CR-142 验收发现并修复）")
    void publishCommandToleratesMissingAnonymous() {
        tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();
        PublishPostCommand command = mapper.readValue(
                "{\"boardCode\":\"qna\",\"title\":\"标题\",\"contentMd\":\"正文\"}", PublishPostCommand.class);

        assertThat(command.anonymous()).isNull();
    }

    @Test
    @DisplayName("发帖入参：anonymous 缺省（null）落库为非匿名——与契约「可不传、缺省 false」一致")
    void nullAnonymousPublishesAsNonAnonymous() {
        when(boardRepository.findByCode("qna"))
                .thenReturn(Optional.of(board(1L, "qna", BoardType.QUESTION)));
        when(moderationGateway.moderate(any()))
                .thenReturn(com.campuslink.module.forum.domain.gateway.ModerationGateway.ModerationVerdict.ok());
        when(postRepository.save(any())).thenAnswer(invocation -> {
            Post saved = invocation.getArgument(0);
            return Post.rehydrate(123L, saved.getBoardId(), saved.getAuthorId(), saved.isAnonymous(),
                    saved.getType(), saved.getTitle(), saved.getContentMd(), saved.getContentHtml(), saved.getStatus(),
                    saved.getReplyCount(), saved.getLikeCount(), saved.getViewCount(), saved.getCoverUrl(),
                    saved.getTags(), saved.isAccepted(), saved.getAcceptedReplyId(), null, null);
        });

        service.publish(42L, new PublishPostCommand("qna", "标题", "正文", null, null));

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        assertThat(captor.getValue().isAnonymous()).isFalse();
    }

    @Test
    @DisplayName("标题命中机审 → 5001，且不落库、正文机审不再触达")
    void moderatedTitleBlocksBeforeContent() {
        when(boardRepository.findByCode("qna"))
                .thenReturn(Optional.of(board(1L, "qna", BoardType.QUESTION)));
        when(moderationGateway.moderate("违规标题"))
                .thenReturn(com.campuslink.module.forum.domain.gateway.ModerationGateway.ModerationVerdict
                        .blocked("内容包含不允许的词汇"));

        assertThatThrownBy(() -> service.publish(42L,
                new PublishPostCommand("qna", "违规标题", "正常正文", null, false)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.MODERATION_BLOCKED));

        verify(postRepository, never()).save(any());
        verify(moderationGateway, never()).moderate("正常正文");
    }

    @Test
    @DisplayName("标签命中机审 → 5001，且不落库（标题 / 正文已放行）")
    void moderatedTagBlocksPublication() {
        when(boardRepository.findByCode("qna"))
                .thenReturn(Optional.of(board(1L, "qna", BoardType.QUESTION)));
        var ok = com.campuslink.module.forum.domain.gateway.ModerationGateway.ModerationVerdict.ok();
        when(moderationGateway.moderate("正常标题")).thenReturn(ok);
        when(moderationGateway.moderate("正常正文")).thenReturn(ok);
        when(moderationGateway.moderate("违禁词"))
                .thenReturn(com.campuslink.module.forum.domain.gateway.ModerationGateway.ModerationVerdict
                        .blocked("内容包含不允许的词汇"));

        assertThatThrownBy(() -> service.publish(42L,
                new PublishPostCommand("qna", "正常标题", "正常正文", List.of("违禁词"), false)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.MODERATION_BLOCKED));

        verify(postRepository, never()).save(any());
    }

    @Test
    @DisplayName("未知 / 停用的版块 code → 3001，不落库")
    void unknownBoardIsNotFound() {
        when(boardRepository.findByCode("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.publish(42L, new PublishPostCommand("nope", "标题", "正文", null, false)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_FOUND));

        verify(postRepository, never()).save(any());
    }

    @Test
    @DisplayName("采纳：提问者采纳他人回复 → posts 定向更新 + replies 旧标志清理/新标志置位")
    void acceptUpdatesPostAndReplyFlags() {
        when(postRepository.findById(123L)).thenReturn(Optional.of(questionPost(42L)));
        when(replyRepository.findVisibleById(456L)).thenReturn(Optional.of(reply(456L, 999L)));

        service.acceptReply(42L, 123L, 456L);

        verify(postRepository).updateAcceptedReply(123L, 456L);
        verify(replyRepository).updateAcceptedFlags(123L, 456L);
        // 采纳通知发给被采纳楼层的作者（F-SOC-001）
        verify(notificationService).replyAccepted(42L, 456L, 999L);
    }

    @Test
    @DisplayName("采纳：非提问者 → 4002，不触达任何写操作")
    void acceptByNonAskerIsForbidden() {
        when(postRepository.findById(123L)).thenReturn(Optional.of(questionPost(42L)));

        assertThatThrownBy(() -> service.acceptReply(777L, 123L, 456L))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.FORBIDDEN));

        verify(postRepository, never()).updateAcceptedReply(any(), any());
        verify(replyRepository, never()).updateAcceptedFlags(any(), any());
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("采纳：回复不存在或不属于该帖 → 3001（防按 id 探测），不触达写操作")
    void acceptForeignReplyIsNotFound() {
        when(postRepository.findById(123L)).thenReturn(Optional.of(questionPost(42L)));
        when(replyRepository.findVisibleById(456L)).thenReturn(Optional.of(reply(456L, 999L, 999L)));

        assertThatThrownBy(() -> service.acceptReply(42L, 123L, 456L))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_FOUND));

        verify(postRepository, never()).updateAcceptedReply(any(), any());
        verify(replyRepository, never()).updateAcceptedFlags(any(), any());
    }

    @Test
    @DisplayName("采纳：不可见（已下架 / 已删除）回复 → 3001，且不触达写操作与通知")
    void acceptInvisibleReplyIsNotFound() {
        when(postRepository.findById(123L)).thenReturn(Optional.of(questionPost(42L)));
        when(replyRepository.findVisibleById(456L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.acceptReply(42L, 123L, 456L))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_FOUND));

        // 守卫只在读侧这一处：用例必须走带 status 过滤的读法，而不是另在写侧补条件
        verify(replyRepository).findVisibleById(456L);
        verify(postRepository, never()).updateAcceptedReply(any(), any());
        verify(replyRepository, never()).updateAcceptedFlags(any(), any());
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("删除：作者本人 → 写墓碑 + 同事务记审计（detail 只带版块与截断标题）")
    void deleteByAuthorMarksTombstoneAndAudits() {
        when(postRepository.findById(123L)).thenReturn(Optional.of(questionPost(42L)));
        when(postRepository.markDeleted(123L)).thenReturn(true);

        service.deletePost(42L, 123L);

        verify(postRepository).markDeleted(123L);
        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(auditService).record(eq(42L), eq("POST_DELETE"), eq("posts"), eq(123L), detail.capture());
        assertThat(detail.getValue()).isEqualTo("boardId=1 title=标题");
    }

    @Test
    @DisplayName("删除：非作者 → 4002，墓碑与审计一条都不写")
    void deleteByNonAuthorIsForbidden() {
        when(postRepository.findById(123L)).thenReturn(Optional.of(questionPost(42L)));

        assertThatThrownBy(() -> service.deletePost(777L, 123L))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.FORBIDDEN));

        verify(postRepository, never()).markDeleted(any());
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("删除：不存在 / 已是墓碑 → 3001（与「不可读统一 404、不区分原因」同口径），不写墓碑与审计")
    void deleteMissingPostIsNotFound() {
        when(postRepository.findById(123L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deletePost(42L, 123L))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_FOUND));

        verify(postRepository, never()).markDeleted(any());
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("删除：已下架（status=REMOVED）但未删除的帖子 → 3001，作者也不能借此绕过处置")
    void deleteRemovedPostIsNotFound() {
        when(postRepository.findById(123L)).thenReturn(Optional.of(post(PostStatus.REMOVED, 42L)));

        assertThatThrownBy(() -> service.deletePost(42L, 123L))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_FOUND));

        verify(postRepository, never()).markDeleted(any());
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("删除：并发下墓碑已被他人写入（0 行受影响）→ 3001 且不记第二条审计")
    void concurrentLosingDeleteDoesNotAudit() {
        when(postRepository.findById(123L)).thenReturn(Optional.of(questionPost(42L)));
        when(postRepository.markDeleted(123L)).thenReturn(false);

        assertThatThrownBy(() -> service.deletePost(42L, 123L))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_FOUND));

        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("编辑：作者本人 → 定向改列、标签整组替换（规范化）、同事务记 POST_UPDATE")
    void reviseByAuthorUpdatesColumnsTagsAndAudits() {
        when(postRepository.findById(123L)).thenReturn(Optional.of(questionPost(42L)));
        when(moderationGateway.moderate(any()))
                .thenReturn(com.campuslink.module.forum.domain.gateway.ModerationGateway.ModerationVerdict.ok());
        when(postRepository.revise(eq(123L), anyString(), anyString(), anyString(), isNull(), any()))
                .thenReturn(true);

        service.revisePost(42L, 123L, new UpdatePostCommand("  新标题  ", "**新正文**", List.of(" Java ")));

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(postRepository).revise(eq(123L), eq("新标题"), eq("**新正文**"), html.capture(),
                isNull(), eq(List.of("Java")));
        assertThat(html.getValue()).contains("<strong>新正文</strong>");
        verify(tagRepository).replaceForPost(123L, List.of("Java"));
        verify(auditService).record(eq(42L), eq("POST_UPDATE"), eq("posts"), eq(123L),
                eq("boardId=1 title=标题"));
    }

    @Test
    @DisplayName("编辑：非作者 → 4002，不写、不替换标签、不审计")
    void reviseByNonAuthorIsForbidden() {
        when(postRepository.findById(123L)).thenReturn(Optional.of(questionPost(42L)));

        assertThatThrownBy(() -> service.revisePost(777L, 123L,
                new UpdatePostCommand("新标题", "新正文", null)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.FORBIDDEN));

        verify(postRepository, never()).revise(any(), any(), any(), any(), any(), any());
        verifyNoInteractions(tagRepository, auditService);
    }

    @Test
    @DisplayName("编辑：已下架（status=REMOVED）→ 3001，作者也不能改被处置内容")
    void reviseRemovedPostIsNotFound() {
        when(postRepository.findById(123L)).thenReturn(Optional.of(post(PostStatus.REMOVED, 42L)));

        assertThatThrownBy(() -> service.revisePost(42L, 123L,
                new UpdatePostCommand("新标题", "新正文", null)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_FOUND));

        verify(postRepository, never()).revise(any(), any(), any(), any(), any(), any());
        verifyNoInteractions(tagRepository, auditService);
    }

    @Test
    @DisplayName("编辑：标签命中机审 → 5001，且不落库、不替换标签")
    void moderatedTagBlocksRevision() {
        when(postRepository.findById(123L)).thenReturn(Optional.of(questionPost(42L)));
        var ok = com.campuslink.module.forum.domain.gateway.ModerationGateway.ModerationVerdict.ok();
        when(moderationGateway.moderate("新标题")).thenReturn(ok);
        when(moderationGateway.moderate("新正文")).thenReturn(ok);
        when(moderationGateway.moderate("违禁词"))
                .thenReturn(com.campuslink.module.forum.domain.gateway.ModerationGateway.ModerationVerdict
                        .blocked("内容包含不允许的词汇"));

        assertThatThrownBy(() -> service.revisePost(42L, 123L,
                new UpdatePostCommand("新标题", "新正文", List.of("违禁词"))))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.MODERATION_BLOCKED));

        verify(postRepository, never()).revise(any(), any(), any(), any(), any(), any());
        verifyNoInteractions(tagRepository);
    }

    @Test
    @DisplayName("编辑：并发下帖子已被下架 / 删除（0 行受影响）→ 3001，不替换标签、不记审计")
    void concurrentLosingRevisionIsNotFound() {
        when(postRepository.findById(123L)).thenReturn(Optional.of(questionPost(42L)));
        when(moderationGateway.moderate(any()))
                .thenReturn(com.campuslink.module.forum.domain.gateway.ModerationGateway.ModerationVerdict.ok());
        when(postRepository.revise(eq(123L), anyString(), anyString(), anyString(), isNull(), any()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.revisePost(42L, 123L,
                new UpdatePostCommand("新标题", "新正文", null)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_FOUND));

        verifyNoInteractions(tagRepository, auditService);
    }

    private static Post questionPost(Long authorId) {
        return Post.rehydrate(123L, 1L, authorId, BoardType.QUESTION, "标题", "正文", "<p>正文</p>",
                PostStatus.PUBLISHED, 1, 0, false, null, null, null);
    }

    private static Post post(PostStatus status, Long authorId) {
        return Post.rehydrate(123L, 1L, authorId, BoardType.QUESTION, "标题", "正文", "<p>正文</p>",
                status, 1, 0, false, null, null, null);
    }

    private static Reply reply(Long id, Long authorId, Long postId) {
        return Reply.rehydrate(id, postId, authorId, 1, "内容", "<p>内容</p>", null, false, 0, null);
    }

    private static Reply reply(Long id, Long authorId) {
        return reply(id, authorId, 123L);
    }

    private static Board board(Long id, String code, BoardType type) {
        return Board.rehydrate(id, code, code, "描述", type, 1);
    }
}
