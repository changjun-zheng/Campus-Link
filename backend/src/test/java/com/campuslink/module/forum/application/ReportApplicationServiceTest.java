package com.campuslink.module.forum.application;

import com.campuslink.common.audit.AuditService;
import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.account.application.AccountApplicationService;
import com.campuslink.module.forum.domain.gateway.PageResult;
import com.campuslink.module.forum.domain.gateway.PostRepository;
import com.campuslink.module.forum.domain.gateway.ReportRepository;
import com.campuslink.module.forum.domain.gateway.ReplyRepository;
import com.campuslink.module.forum.domain.model.PostStatus;
import com.campuslink.module.forum.domain.model.Report;
import com.campuslink.module.forum.domain.model.ReplyStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 举报工单闭环用例 {@link ReportApplicationService}（F-SAFE-002 / CR-082）。
 *
 * <p>守的是**闭环的边界**：① 提交侧的三个拒绝（目标类型 / 理由词表 / 24h 去重）各自独立，
 * 词表外的取值不能落库；② 运营分页的 page / size 在应用层归一（1 ~ 100）——
 * 契约里的「归一化后的值」就靠这里；③ 处理侧的四条收场：不存在 → 3001、已闭环 → **零副作用幂等**、
 * action 非法 → 1001（此时不得动任何目标）、并发被别人先关 → 不再记审计。
 *
 * <p>本类不证明处置真的写进了数据库：{@code PostRepository#updateStatus} / {@code ReplyRepository#updateStatus}
 * 的定向 UPDATE 结构由各自的仓储测试守，这里只断言"该调用谁、带了什么参数"。
 */
@ExtendWith(MockitoExtension.class)
class ReportApplicationServiceTest {

    private static final long OPERATOR_ID = 1L;
    private static final long REPORTER_ID = 42L;
    private static final long REPORT_ID = 9L;
    private static final long TARGET_ID = 7L;
    private static final Instant CREATED_AT = Instant.parse("2026-10-15T08:00:00Z");

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private ReplyRepository replyRepository;
    @Mock
    private AccountApplicationService accountApplicationService;
    @Mock
    private AuditService auditService;

    private ReportApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ReportApplicationService(reportRepository, postRepository, replyRepository,
                accountApplicationService, auditService);
    }

    @Test
    @DisplayName("提交：目标类型不在 {POST, REPLY, USER} → 1001，连去重查询都不发")
    void submitRejectsUnknownTargetType() {
        assertThatThrownBy(() -> service.submit(REPORTER_ID, "BOARD", TARGET_ID, "SPAM", null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));

        verifyNoInteractions(reportRepository, auditService);
    }

    @Test
    @DisplayName("提交：理由为空或不在词表 → 1001")
    void submitRejectsMissingReason() {
        assertThatThrownBy(() -> service.submit(REPORTER_ID, Report.TARGET_POST, TARGET_ID, null, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));
        assertThatThrownBy(() -> service.submit(REPORTER_ID, Report.TARGET_POST, TARGET_ID, "BORING", null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));
    }

    @Test
    @DisplayName("提交：24h 内重复举报同一对象 → 429 / 2012，不建单不审计")
    void submitRejectsDuplicateWithinWindow() {
        when(reportRepository.existsRecentByReporterAndTarget(eq(REPORTER_ID), eq(Report.TARGET_POST),
                eq(TARGET_ID), any())).thenReturn(true);

        assertThatThrownBy(() -> service.submit(REPORTER_ID, Report.TARGET_POST, TARGET_ID, "SPAM", "广告"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.REPORT_DUPLICATED));

        verify(reportRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("提交成功：建 PENDING 工单，审计里带 reportId（运营排查看得到具体工单）")
    void submitCreatesPendingTicketAndAudits() {
        when(reportRepository.existsRecentByReporterAndTarget(any(), anyString(), anyLong(), any()))
                .thenReturn(false);
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> {
            Report r = inv.getArgument(0);
            return new Report(REPORT_ID, r.reporterId(), r.targetType(), r.targetId(), r.reason(),
                    r.detail(), r.status(), null, null, r.createdAt(), null);
        });

        Report saved = service.submit(REPORTER_ID, Report.TARGET_POST, TARGET_ID, "SPAM", "广告");

        assertThat(saved.id()).isEqualTo(REPORT_ID);

        ArgumentCaptor<Report> created = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(created.capture());
        assertThat(created.getValue().status()).isEqualTo("PENDING");
        assertThat(created.getValue().reporterId()).isEqualTo(REPORTER_ID);
        assertThat(created.getValue().detail()).isEqualTo("广告");

        verify(auditService).record(REPORTER_ID, "REPORT_SUBMIT", Report.TARGET_POST, TARGET_ID,
                "reportId=" + REPORT_ID);
    }

    @Test
    @DisplayName("运营分页：page 下限 1、size 上限 100 在应用层归一（契约回显即实际查询）")
    void adminPageClampsPaging() {
        when(reportRepository.findPage(any(), anyInt(), anyInt()))
                .thenAnswer(inv -> new PageResult<Report>(List.of(), 0,
                        inv.getArgument(1), inv.getArgument(2)));

        PageResult<Report> clamped = service.adminPage("PENDING", 0, 500);

        assertThat(clamped.page()).isEqualTo(1);
        assertThat(clamped.size()).isEqualTo(100);
        verify(reportRepository).findPage("PENDING", 1, 100);
    }

    @Test
    @DisplayName("运营分页：合法范围内的 page / size 原样透传（不得顺手改写）")
    void adminPagePassesThroughWithinRange() {
        when(reportRepository.findPage("CLOSED", 3, 20))
                .thenReturn(new PageResult<>(List.of(), 0, 3, 20));

        service.adminPage("CLOSED", 3, 20);

        verify(reportRepository).findPage("CLOSED", 3, 20);
    }

    @Test
    @DisplayName("处理：工单不存在 → 3001，不写审计、不动目标")
    void closeMissingTicketIsNotFound() {
        when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.close(OPERATOR_ID, REPORT_ID, "REMOVE", null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_FOUND));

        verifyNoInteractions(postRepository, replyRepository, accountApplicationService, auditService);
    }

    @Test
    @DisplayName("处理：工单已 CLOSED → 幂等返回（重复处置不记审计、不再下架任何内容）")
    void closeClosedTicketIsIdempotent() {
        when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(report("CLOSED", Report.TARGET_POST)));

        service.close(OPERATOR_ID, REPORT_ID, "REMOVE", null);

        verify(reportRepository, never()).close(anyLong(), anyString(), anyLong());
        verifyNoInteractions(postRepository, replyRepository, accountApplicationService, auditService);
    }

    @Test
    @DisplayName("处理：action 既非 REMOVE 也非 DISMISS → 1001，且**先校验后动手**（目标未被下架）")
    void closeRejectsUnknownActionBeforeTouchingTarget() {
        when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(report("PENDING", Report.TARGET_POST)));

        assertThatThrownBy(() -> service.close(OPERATOR_ID, REPORT_ID, "BAN", null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));

        verifyNoInteractions(postRepository, replyRepository, accountApplicationService, auditService);
    }

    @Test
    @DisplayName("REMOVE 帖子：定向下架 PUBLISHED → REMOVED，闭环后写审计")
    void closeRemovesPostAndAudits() {
        when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(report("PENDING", Report.TARGET_POST)));
        when(reportRepository.close(REPORT_ID, "REMOVE", OPERATOR_ID)).thenReturn(true);

        service.close(OPERATOR_ID, REPORT_ID, "REMOVE", null);

        verify(postRepository).updateStatus(TARGET_ID, PostStatus.REMOVED, PostStatus.PUBLISHED);
        verify(auditService).record(OPERATOR_ID, "REPORT_CLOSE", Report.TARGET_POST, TARGET_ID,
                "reportId=%s action=REMOVE".formatted(REPORT_ID));
    }

    @Test
    @DisplayName("REMOVE 楼层：下架走 replies 的定向 UPDATE（不得误伤父帖）")
    void closeRemovesReply() {
        when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(report("PENDING", Report.TARGET_REPLY)));
        when(reportRepository.close(REPORT_ID, "REMOVE", OPERATOR_ID)).thenReturn(true);

        service.close(OPERATOR_ID, REPORT_ID, "REMOVE", null);

        verify(replyRepository).updateStatus(TARGET_ID, ReplyStatus.REMOVED, ReplyStatus.PUBLISHED);
        verifyNoInteractions(postRepository);
    }

    @Test
    @DisplayName("REMOVE 用户：经 account 应用服务封禁（跨上下文只调对方 application）")
    void closeRemovesUserThroughAccountService() {
        when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(report("PENDING", Report.TARGET_USER)));
        when(reportRepository.close(REPORT_ID, "REMOVE", OPERATOR_ID)).thenReturn(true);

        service.close(OPERATOR_ID, REPORT_ID, "REMOVE", null);

        verify(accountApplicationService).adminBan(OPERATOR_ID, TARGET_ID);
        verifyNoInteractions(postRepository, replyRepository);
    }

    @Test
    @DisplayName("DISMISS：不动任何目标，resolution 只写动作名")
    void closeDismissTouchesNothing() {
        when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(report("PENDING", Report.TARGET_POST)));
        when(reportRepository.close(REPORT_ID, "DISMISS", OPERATOR_ID)).thenReturn(true);

        service.close(OPERATOR_ID, REPORT_ID, "DISMISS", " ");

        verify(reportRepository).close(REPORT_ID, "DISMISS", OPERATOR_ID);
        verifyNoInteractions(postRepository, replyRepository, accountApplicationService);
        verify(auditService).record(OPERATOR_ID, "REPORT_CLOSE", Report.TARGET_POST, TARGET_ID,
                "reportId=%s action=DISMISS".formatted(REPORT_ID));
    }

    @Test
    @DisplayName("带备注的处理：resolution 拼成「动作：备注」，非空白备注才拼")
    void closeAppendsNoteToResolution() {
        when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(report("PENDING", Report.TARGET_REPLY)));
        when(reportRepository.close(REPORT_ID, "REMOVE：重复内容", OPERATOR_ID)).thenReturn(true);

        service.close(OPERATOR_ID, REPORT_ID, "REMOVE", "重复内容");

        verify(reportRepository).close(REPORT_ID, "REMOVE：重复内容", OPERATOR_ID);
    }

    @Test
    @DisplayName("REMOVE：库里 target_type 是未知词表值（脏数据）→ 1001，工单不得被静默闭环")
    void closeRejectsUnknownStoredTargetType() {
        when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(report("PENDING", "BOARD")));

        assertThatThrownBy(() -> service.close(OPERATOR_ID, REPORT_ID, "REMOVE", null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));

        verify(reportRepository, never()).close(anyLong(), anyString(), anyLong());
        verifyNoInteractions(postRepository, replyRepository, accountApplicationService, auditService);
    }

    @Test
    @DisplayName("并发收场：close 返回 false（别人先闭环）→ 直接返回，不重复审计")
    void concurrentCloseSkipsAudit() {
        when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(report("PENDING", Report.TARGET_POST)));
        when(reportRepository.close(REPORT_ID, "REMOVE", OPERATOR_ID)).thenReturn(false);

        service.close(OPERATOR_ID, REPORT_ID, "REMOVE", null);

        verifyNoInteractions(auditService);
    }

    // ---------- helpers ----------

    private static Report report(String status, String targetType) {
        return new Report(REPORT_ID, REPORTER_ID, targetType, TARGET_ID, "SPAM", "广告",
                status, null, null, CREATED_AT, null);
    }
}
