package com.campuslink.module.forum.application;

import com.campuslink.common.audit.AuditService;
import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.forum.application.cmd.ModerationCommand;
import com.campuslink.module.forum.domain.gateway.PostRepository;
import com.campuslink.module.forum.domain.gateway.ReplyRepository;
import com.campuslink.module.forum.domain.model.BoardType;
import com.campuslink.module.forum.domain.model.Post;
import com.campuslink.module.forum.domain.model.PostStatus;
import com.campuslink.module.forum.domain.model.Reply;
import com.campuslink.module.forum.domain.model.ReplyStatus;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 内容处置用例（F-SAFE-003 / CR-066）：状态迁移、幂等零审计、并发失败出口、审计口径。
 *
 * <p>
 * 本类最要紧的一组断言是**零副作用**：同状态重复调用与"条件更新 0 行"都必须一条审计都不写
 * （审计是"谁在何时处置了什么"的账本，重复记账即失真）；另一组是**处置不碰作者墓碑**——
 * 断言 detail 里没有 is_deleted 痕迹做不到，改为断言只调 {@code updateStatus}、不调
 * {@code markDeleted}。
 */
@ExtendWith(MockitoExtension.class)
class ContentModerationApplicationServiceTest {

    private static final long OPERATOR = 1L;
    private static final long POST_ID = 123L;
    private static final long REPLY_ID = 11L;

    @Mock
    private PostRepository postRepository;
    @Mock
    private ReplyRepository replyRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private NotificationApplicationService notificationService;

    private ContentModerationApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ContentModerationApplicationService(postRepository, replyRepository, auditService,
                notificationService);
    }

    @Test
    @DisplayName("帖子：PUBLISHED → REMOVED 写 status 并记 POST_REMOVE，detail 带前后态与版块标题")
    void removePostWritesStatusAndAudit() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post(PostStatus.PUBLISHED)));
        when(postRepository.updateStatus(POST_ID, PostStatus.REMOVED, PostStatus.PUBLISHED)).thenReturn(true);

        service.moderatePost(OPERATOR, POST_ID, command("REMOVED", "广告内容"));

        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(auditService).record(eq(OPERATOR), eq("POST_REMOVE"), eq("posts"), eq(POST_ID), detail.capture());
        assertThat(detail.getValue())
                .isEqualTo("from=PUBLISHED to=REMOVED reason=广告内容 boardId=1 title=标题");
        // 处置只写 status，绝不写作者墓碑（两列两义，删与下架必须能分开追问）
        verify(postRepository, never()).markDeleted(anyLong());
        verify(notificationService).contentRemovedPost(OPERATOR, POST_ID, 42L);
    }

    @Test
    @DisplayName("帖子：REMOVED → PUBLISHED 恢复，记 POST_RESTORE（恢复的前提是读得到当前 REMOVED）")
    void restorePostWritesStatusAndAudit() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post(PostStatus.REMOVED)));
        when(postRepository.updateStatus(POST_ID, PostStatus.PUBLISHED, PostStatus.REMOVED)).thenReturn(true);

        service.moderatePost(OPERATOR, POST_ID, command("PUBLISHED", "误判已复核"));

        verify(auditService).record(eq(OPERATOR), eq("POST_RESTORE"), eq("posts"), eq(POST_ID),
                eq("from=REMOVED to=PUBLISHED reason=误判已复核 boardId=1 title=标题"));
        verify(notificationService).contentRestoredPost(OPERATOR, POST_ID, 42L);
    }

    @Test
    @DisplayName("帖子：当前态即目标态 → 幂等返回，不写 status、不记审计")
    void sameStatusIsIdempotentAndUnaudited() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post(PostStatus.REMOVED)));

        service.moderatePost(OPERATOR, POST_ID, command("REMOVED", "再点一次"));

        verify(postRepository, never()).updateStatus(any(), any(), any());
        verifyNoInteractions(auditService);
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("帖子：不存在或已被作者删除（墓碑行读不到）→ 3001，不写 status、不记审计")
    void missingOrTombstonedPostIsNotFound() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.moderatePost(OPERATOR, POST_ID, command("REMOVED", null)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_FOUND));

        verify(postRepository, never()).updateStatus(any(), any(), any());
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("帖子：条件更新 0 行（读→写之间被并发处置改走）→ 3001 且不记审计（CR-060 TOCTOU 残余的出口）")
    void losingRaceDoesNotAudit() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post(PostStatus.PUBLISHED)));
        when(postRepository.updateStatus(POST_ID, PostStatus.REMOVED, PostStatus.PUBLISHED)).thenReturn(false);

        assertThatThrownBy(() -> service.moderatePost(OPERATOR, POST_ID, command("REMOVED", null)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_FOUND));

        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("帖子：reason 空白落 \"-\"、标题超 100 字截到 100 字加省略号（detail 里不出现 null 字面量）")
    void auditDetailNormalizesReasonAndTitle() {
        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.of(post("标".repeat(101), PostStatus.PUBLISHED)));
        when(postRepository.updateStatus(POST_ID, PostStatus.REMOVED, PostStatus.PUBLISHED)).thenReturn(true);

        service.moderatePost(OPERATOR, POST_ID, command("REMOVED", "   "));

        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(auditService).record(eq(OPERATOR), eq("POST_REMOVE"), eq("posts"), eq(POST_ID), detail.capture());
        assertThat(detail.getValue())
                .isEqualTo("from=PUBLISHED to=REMOVED reason=- boardId=1 title=" + "标".repeat(100) + "…");
    }

    @Test
    @DisplayName("楼层：PUBLISHED → REMOVED 走窄读 + 定向 UPDATE，记 REPLY_REMOVE 并通知作者")
    void removeReplyWritesStatusAndAudit() {
        when(replyRepository.findStatusById(REPLY_ID)).thenReturn(Optional.of(ReplyStatus.PUBLISHED));
        when(replyRepository.updateStatus(REPLY_ID, ReplyStatus.REMOVED, ReplyStatus.PUBLISHED)).thenReturn(true);
        when(replyRepository.findByIds(List.of(REPLY_ID))).thenReturn(List.of(reply(42L)));

        service.moderateReply(OPERATOR, REPLY_ID, command("REMOVED", "人身攻击"));

        verify(auditService).record(OPERATOR, "REPLY_REMOVE", "replies", REPLY_ID,
                "from=PUBLISHED to=REMOVED reason=人身攻击");
        // 窄读而非 findVisibleById：恢复场景必须读得到 REMOVED，故不许退化成"不可见即不存在"
        verify(replyRepository, never()).findVisibleById(any());
        verify(replyRepository, never()).adjustLikeCount(any(), eq(1));
        verify(notificationService).contentRemovedReply(OPERATOR, REPLY_ID, 42L);
    }

    @Test
    @DisplayName("楼层：REMOVED → PUBLISHED 记 REPLY_RESTORE 并通知作者")
    void restoreReplyAudits() {
        when(replyRepository.findStatusById(REPLY_ID)).thenReturn(Optional.of(ReplyStatus.REMOVED));
        when(replyRepository.updateStatus(REPLY_ID, ReplyStatus.PUBLISHED, ReplyStatus.REMOVED)).thenReturn(true);
        when(replyRepository.findByIds(List.of(REPLY_ID))).thenReturn(List.of(reply(42L)));

        service.moderateReply(OPERATOR, REPLY_ID, command("PUBLISHED", null));

        verify(auditService).record(OPERATOR, "REPLY_RESTORE", "replies", REPLY_ID,
                "from=REMOVED to=PUBLISHED reason=-");
        verify(notificationService).contentRestoredReply(OPERATOR, REPLY_ID, 42L);
    }

    @Test
    @DisplayName("楼层：同状态重复处置 → 幂等返回，不写 status、不记审计")
    void sameReplyStatusIsIdempotent() {
        when(replyRepository.findStatusById(REPLY_ID)).thenReturn(Optional.of(ReplyStatus.REMOVED));

        service.moderateReply(OPERATOR, REPLY_ID, command("REMOVED", "再点一次"));

        verify(replyRepository, never()).updateStatus(any(), any(), any());
        verifyNoInteractions(auditService);
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("楼层：不存在或已是作者墓碑 → 3001（与帖子侧同为「内容不可读」，不区分原因）")
    void missingOrTombstonedReplyIsNotFound() {
        when(replyRepository.findStatusById(REPLY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.moderateReply(OPERATOR, REPLY_ID, command("REMOVED", null)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_FOUND));

        verify(replyRepository, never()).updateStatus(any(), any(), any());
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("楼层：条件更新 0 行 → 3001 且不记审计")
    void losingReplyRaceDoesNotAudit() {
        when(replyRepository.findStatusById(REPLY_ID)).thenReturn(Optional.of(ReplyStatus.PUBLISHED));
        when(replyRepository.updateStatus(REPLY_ID, ReplyStatus.REMOVED, ReplyStatus.PUBLISHED)).thenReturn(false);

        assertThatThrownBy(() -> service.moderateReply(OPERATOR, REPLY_ID, command("REMOVED", null)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_FOUND));

        verifyNoInteractions(auditService);
    }

    private static Reply reply(long authorId) {
        return Reply.rehydrate(REPLY_ID, POST_ID, authorId, 1, "正文", "<p>正文</p>", null, false, 0, null);
    }

    private static Post post(PostStatus status) {
        return post("标题", status);
    }

    private static Post post(String title, PostStatus status) {
        return Post.rehydrate(POST_ID, 1L, 42L, BoardType.QUESTION, title, "正文", "<p>正文</p>",
                status, 1, 0, false, null, null, null);
    }

    private static ModerationCommand command(String status, String reason) {
        return new ModerationCommand(status, reason);
    }
}
