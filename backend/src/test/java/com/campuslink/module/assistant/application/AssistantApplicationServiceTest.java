package com.campuslink.module.assistant.application;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.markdown.MarkdownRenderer;
import com.campuslink.common.result.ResultCode;
import com.campuslink.config.AppProperties;
import com.campuslink.module.assistant.application.cmd.AssistantCommands;
import com.campuslink.module.assistant.domain.gateway.AssistantUsageGateway;
import com.campuslink.module.assistant.domain.gateway.ChatModelException;
import com.campuslink.module.assistant.domain.gateway.ChatModelGateway;
import com.campuslink.module.assistant.domain.gateway.ChatModelGateway.ChatCompletion;
import com.campuslink.module.assistant.domain.gateway.ConversationStore;
import com.campuslink.module.assistant.domain.model.AssistantConversation;
import com.campuslink.module.assistant.domain.service.AssistantPromptBuilder;
import com.campuslink.module.forum.application.ForumQueryApplicationService;
import com.campuslink.module.forum.application.ModerationApplicationService;
import com.campuslink.module.forum.application.cmd.ForumResults.PostDetail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantApplicationServiceTest {

    private static final long USER_ID = 7L;
    private static final long POST_ID = 42L;

    @Mock
    private ForumQueryApplicationService forumQuery;
    @Mock
    private ModerationApplicationService moderation;
    @Mock
    private ChatModelGateway chatModel;
    @Mock
    private ConversationStore conversations;
    @Mock
    private AssistantUsageGateway usage;

    private AppProperties props;
    private AssistantApplicationService service;

    @BeforeEach
    void setUp() {
        props = new AppProperties();
        AppProperties.Assistant config = props.getAssistant();
        config.setEnabled(true);
        config.setApiKey("test-key");
        config.setMaxSelectedChars(20);
        config.setMaxQuestionChars(10);
        config.setMaxUserTurns(2);
        config.setMaxOutputTokens(100);
        config.setTemperature(0.1);
        config.setModerateOutput(true);
        config.setSessionTtlMinutes(30);
        config.setSseTimeoutSeconds(120);
        config.setQuotaZone("Asia/Shanghai");
        service = new AssistantApplicationService(props, forumQuery, moderation,
                new AssistantPromptBuilder(), chatModel, conversations, usage,
                new MarkdownRenderer(), new DirectExecutor());
    }

    @Test
    @DisplayName("开始会话：流式增量、完成帧、会话落 Redis、释放并发锁")
    void startStreamsAndSaves() {
        stubStart();
        stubModel("答案");

        RecordingObserver observer = new RecordingObserver();
        service.start(USER_ID, new AssistantCommands.StartConversation(POST_ID, "选中内容", "问一下"))
                .start(observer);

        assertThat(observer.conversationId).isNotBlank();
        assertThat(observer.remainingToday).isEqualTo(19);
        assertThat(observer.deltas.toString()).isEqualTo("答案");
        assertThat(observer.error).isNull();
        assertThat(observer.done.raw()).isEqualTo("答案");
        assertThat(observer.done.html()).contains("答案");
        assertThat(observer.done.blocked()).isFalse();

        ArgumentCaptor<AssistantConversation> saved = ArgumentCaptor.forClass(AssistantConversation.class);
        verify(conversations).save(saved.capture(), eq(Duration.ofMinutes(30)));
        assertThat(saved.getValue().messages()).hasSize(2);
        assertThat(saved.getValue().messages().get(0).content()).isEqualTo("问一下");
        verify(usage).release(USER_ID, "lease");
        verify(moderation, times(2)).ensureAllowed(anyString());
    }

    @Test
    @DisplayName("关闭开关：7001")
    void disabledFeatureIsRejected() {
        props.getAssistant().setEnabled(false);

        assertThatThrownBy(() -> service.start(USER_ID,
                new AssistantCommands.StartConversation(POST_ID, "选中", "问")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.ASSISTANT_DISABLED));
    }

    @Test
    @DisplayName("选区或问题超长：7004，且不查帖子")
    void overlongInputIsRejected() {
        assertThatThrownBy(() -> service.start(USER_ID,
                new AssistantCommands.StartConversation(POST_ID, "选".repeat(21), "问")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.ASSISTANT_INPUT_TOO_LONG));
        assertThatThrownBy(() -> service.start(USER_ID,
                new AssistantCommands.StartConversation(POST_ID, "选中", "问".repeat(11))))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.ASSISTANT_INPUT_TOO_LONG));
        verify(forumQuery, never()).postDetail(anyLong(), anyLong());
    }

    @Test
    @DisplayName("帖子不可见：沿用 forum 的 3001")
    void missingPostPropagates() {
        when(forumQuery.postDetail(POST_ID, USER_ID)).thenThrow(new ApiException(ResultCode.NOT_FOUND));

        assertThatThrownBy(() -> service.start(USER_ID,
                new AssistantCommands.StartConversation(POST_ID, "选中", "问")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_FOUND));
        verify(usage, never()).acquire(anyLong(), anyInt(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("输入机审命中：5001，不扣配额")
    void inputModerationBlocks() {
        when(forumQuery.postDetail(POST_ID, USER_ID)).thenReturn(detail());
        doThrow(new ApiException(ResultCode.MODERATION_BLOCKED)).when(moderation).ensureAllowed(anyString());

        assertThatThrownBy(() -> service.start(USER_ID,
                new AssistantCommands.StartConversation(POST_ID, "选中", "问")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.MODERATION_BLOCKED));
        verify(usage, never()).acquire(anyLong(), anyInt(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("配额 / 限流 / 并发分别映射到 7002 / 7003 / 7007")
    void usageDenialsMapToDistinctCodes() {
        when(forumQuery.postDetail(POST_ID, USER_ID)).thenReturn(detail());
        when(usage.acquire(anyLong(), anyInt(), anyInt(), any(), any()))
                .thenReturn(AssistantUsageGateway.UsageDecision.denied(
                        AssistantUsageGateway.UsageDecision.DenyReason.QUOTA_EXCEEDED))
                .thenReturn(AssistantUsageGateway.UsageDecision.denied(
                        AssistantUsageGateway.UsageDecision.DenyReason.RATE_LIMITED))
                .thenReturn(AssistantUsageGateway.UsageDecision.denied(
                        AssistantUsageGateway.UsageDecision.DenyReason.BUSY));

        assertCode(ResultCode.ASSISTANT_QUOTA_EXCEEDED);
        assertCode(ResultCode.ASSISTANT_RATE_LIMITED);
        assertCode(ResultCode.ASSISTANT_BUSY);
    }

    @Test
    @DisplayName("追问：会话不存在 / 不属于当前用户统一 7005")
    void followUpRejectsMissingOrForeignConversation() {
        when(conversations.find("missing")).thenReturn(Optional.empty());
        assertCode(ResultCode.ASSISTANT_CONVERSATION_NOT_FOUND,
                () -> service.followUp(USER_ID, new AssistantCommands.FollowUp("missing", "追问")));

        AssistantConversation foreign = AssistantConversation.start(8L, POST_ID, "标题", "版块", "选中")
                .withUserMessage("问");
        when(conversations.find("foreign")).thenReturn(Optional.of(foreign));
        assertCode(ResultCode.ASSISTANT_CONVERSATION_NOT_FOUND,
                () -> service.followUp(USER_ID, new AssistantCommands.FollowUp("foreign", "追问")));
    }

    @Test
    @DisplayName("追问轮次达上限：7008，且不扣配额")
    void followUpTurnLimit() {
        AssistantConversation full = AssistantConversation.start(USER_ID, POST_ID, "标题", "版块", "选中")
                .withUserMessage("一").withAssistantMessage("答一")
                .withUserMessage("二").withAssistantMessage("答二");
        when(conversations.find("full")).thenReturn(Optional.of(full));

        assertCode(ResultCode.ASSISTANT_TURN_LIMIT,
                () -> service.followUp(USER_ID, new AssistantCommands.FollowUp("full", "第三问")));
        verify(usage, never()).acquire(anyLong(), anyInt(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("追问成功：在旧会话上追加并保存")
    void followUpAppendsAndSaves() {
        AssistantConversation existing = AssistantConversation.start(USER_ID, POST_ID, "标题", "版块", "选中")
                .withUserMessage("一").withAssistantMessage("答一");
        when(conversations.find("c1")).thenReturn(Optional.of(existing));
        when(usage.acquire(anyLong(), anyInt(), anyInt(), any(), any()))
                .thenReturn(AssistantUsageGateway.UsageDecision.allowed(18, "lease-2"));
        stubModel("答二");

        RecordingObserver observer = new RecordingObserver();
        service.followUp(USER_ID, new AssistantCommands.FollowUp("c1", "二")).start(observer);

        assertThat(observer.remainingToday).isEqualTo(18);
        assertThat(observer.done.raw()).isEqualTo("答二");
        ArgumentCaptor<AssistantConversation> saved = ArgumentCaptor.forClass(AssistantConversation.class);
        verify(conversations).save(saved.capture(), eq(Duration.ofMinutes(30)));
        assertThat(saved.getValue().messages()).hasSize(4);
        verify(usage).release(USER_ID, "lease-2");
    }

    @Test
    @DisplayName("输出机审命中：完成帧替换为安全提示，不把原文当最终结果")
    void outputModerationReplacesAnswer() {
        stubStart();
        doNothing().doThrow(new ApiException(ResultCode.MODERATION_BLOCKED))
                .when(moderation).ensureAllowed(anyString());
        stubModel("原文");

        RecordingObserver observer = new RecordingObserver();
        service.start(USER_ID, new AssistantCommands.StartConversation(POST_ID, "选中", "问")).start(observer);

        assertThat(observer.done.blocked()).isTrue();
        assertThat(observer.done.raw()).doesNotContain("原文").contains("安全检查");
        assertThat(observer.done.html()).doesNotContain("原文");
        ArgumentCaptor<AssistantConversation> saved = ArgumentCaptor.forClass(AssistantConversation.class);
        verify(conversations).save(saved.capture(), any());
        assertThat(saved.getValue().messages().get(1).content()).doesNotContain("原文");
    }

    @Test
    @DisplayName("上游失败：SSE error 7006，不保存半截会话")
    void modelFailureEmitsErrorAndDoesNotSave() {
        stubStart();
        doThrow(new ChatModelException("boom")).when(chatModel).stream(any(), any(), any(), any());

        RecordingObserver observer = new RecordingObserver();
        service.start(USER_ID, new AssistantCommands.StartConversation(POST_ID, "选中", "问")).start(observer);

        assertThat(observer.done).isNull();
        assertThat(observer.error).isEqualTo(ResultCode.ASSISTANT_UNAVAILABLE);
        verify(conversations, never()).save(any(), any());
        verify(usage).release(USER_ID, "lease");
    }

    @Test
    @DisplayName("模型没有输出内容：7006，不保存空回答")
    void emptyAnswerIsTreatedAsFailure() {
        stubStart();
        doAnswer(invocation -> {
            Consumer<ChatCompletion> onComplete = invocation.getArgument(2);
            onComplete.accept(new ChatCompletion("stop", 1, 0));
            return null;
        }).when(chatModel).stream(any(), any(), any(), any());

        RecordingObserver observer = new RecordingObserver();
        service.start(USER_ID, new AssistantCommands.StartConversation(POST_ID, "选中", "问")).start(observer);

        assertThat(observer.error).isEqualTo(ResultCode.ASSISTANT_UNAVAILABLE);
        verify(conversations, never()).save(any(), any());
    }

    @Test
    @DisplayName("公开配置：关闭时不返回快捷问题")
    void configReflectsEnabledState() {
        AssistantResults.AssistantConfig enabled = service.config();
        assertThat(enabled.enabled()).isTrue();
        assertThat(enabled.dailyLimit()).isEqualTo(20);
        assertThat(enabled.quickPrompts()).isNotEmpty();

        props.getAssistant().setEnabled(false);
        AssistantResults.AssistantConfig disabled = service.config();
        assertThat(disabled.enabled()).isFalse();
        assertThat(disabled.quickPrompts()).isEmpty();
    }

    private void stubStart() {
        when(forumQuery.postDetail(POST_ID, USER_ID)).thenReturn(detail());
        when(usage.acquire(anyLong(), anyInt(), anyInt(), any(), any()))
                .thenReturn(AssistantUsageGateway.UsageDecision.allowed(19, "lease"));
    }

    private void stubModel(String answer) {
        doAnswer(invocation -> {
            Consumer<String> onDelta = invocation.getArgument(1);
            Consumer<ChatCompletion> onComplete = invocation.getArgument(2);
            onDelta.accept(answer);
            onComplete.accept(new ChatCompletion("stop", 11, 22));
            return null;
        }).when(chatModel).stream(any(), any(), any(), any());
    }

    private void assertCode(ResultCode code) {
        assertCode(code, () -> service.start(USER_ID,
                new AssistantCommands.StartConversation(POST_ID, "选中", "问")));
    }

    private void assertCode(ResultCode code, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(code));
    }

    private static PostDetail detail() {
        return new PostDetail(POST_ID, "qna", "技术问答", "QA", "标题", "<p>x</p>",
                1L, "作者", null, 0, 0, 0, List.of(), false, false, false, Instant.now(), false);
    }

    private static final class RecordingObserver implements AssistantStreamObserver {
        private String conversationId;
        private int remainingToday;
        private final StringBuilder deltas = new StringBuilder();
        private AssistantResults.AssistantDone done;
        private ResultCode error;

        @Override
        public void onMeta(String conversationId, int remainingToday) {
            this.conversationId = conversationId;
            this.remainingToday = remainingToday;
        }

        @Override
        public void onDelta(String text) {
            deltas.append(text);
        }

        @Override
        public void onDone(AssistantResults.AssistantDone done) {
            this.done = done;
        }

        @Override
        public void onError(ResultCode code) {
            this.error = code;
        }
    }

    private static final class DirectExecutor extends AbstractExecutorService {
        @Override
        public void shutdown() {
        }

        @Override
        public List<Runnable> shutdownNow() {
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }
}
