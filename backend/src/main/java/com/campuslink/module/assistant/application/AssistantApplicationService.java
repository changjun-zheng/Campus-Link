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
import com.campuslink.module.assistant.domain.gateway.ChatStreamControl;
import com.campuslink.module.assistant.domain.gateway.ConversationStore;
import com.campuslink.module.assistant.domain.model.AssistantConversation;
import com.campuslink.module.assistant.domain.service.AssistantPromptBuilder;
import com.campuslink.module.forum.application.ForumQueryApplicationService;
import com.campuslink.module.forum.application.ModerationApplicationService;
import com.campuslink.module.forum.application.cmd.ForumResults.PostDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 选中文本问 AI 的用例编排（CR-200）。
 *
 * <p>预检（开关 / 帖子可见性 / 长度 / 机审 / 配额）全部在返回 handle 之前同步完成，
 * 这样 Controller 可以把失败直接交给 {@code GlobalExceptionHandler} 产出普通 {@code ApiError}；
 * 只有模型流开始后的故障才通过 {@link AssistantStreamObserver#onError(ResultCode)} 走 SSE 错误帧。
 *
 * <p>会话只在成功生成后才写入 Redis：失败 / 取消的轮次不落库，追问不会被半截上下文污染。
 */
@Service
public class AssistantApplicationService {

    private static final Logger log = LoggerFactory.getLogger(AssistantApplicationService.class);

    private static final String BLOCKED_ANSWER = "抱歉，这段回答未通过内容安全检查。请换个问法或缩小选中范围再试。";

    private final AppProperties props;
    private final ForumQueryApplicationService forumQueryApplicationService;
    private final ModerationApplicationService moderationApplicationService;
    private final AssistantPromptBuilder promptBuilder;
    private final ChatModelGateway chatModelGateway;
    private final ConversationStore conversationStore;
    private final AssistantUsageGateway usageGateway;
    private final MarkdownRenderer markdownRenderer;
    private final ExecutorService assistantStreamExecutor;

    public AssistantApplicationService(AppProperties props,
            ForumQueryApplicationService forumQueryApplicationService,
            ModerationApplicationService moderationApplicationService,
            AssistantPromptBuilder promptBuilder,
            ChatModelGateway chatModelGateway,
            ConversationStore conversationStore,
            AssistantUsageGateway usageGateway,
            MarkdownRenderer markdownRenderer,
            @Qualifier("assistantStreamExecutor") ExecutorService assistantStreamExecutor) {
        this.props = props;
        this.forumQueryApplicationService = forumQueryApplicationService;
        this.moderationApplicationService = moderationApplicationService;
        this.promptBuilder = promptBuilder;
        this.chatModelGateway = chatModelGateway;
        this.conversationStore = conversationStore;
        this.usageGateway = usageGateway;
        this.markdownRenderer = markdownRenderer;
        this.assistantStreamExecutor = assistantStreamExecutor;
    }

    /** 公开配置：前端据此决定是否显示「问 AI」入口与字数上限。 */
    public AssistantResults.AssistantConfig config() {
        AppProperties.Assistant config = props.getAssistant();
        return new AssistantResults.AssistantConfig(config.isEnabled(), config.getDailyLimit(),
                config.getMaxSelectedChars(), config.getMaxQuestionChars(), config.getPrivacyNoticeVersion(),
                config.isEnabled() ? List.copyOf(config.getQuickPrompts()) : List.of());
    }

    public AssistantStreamHandle start(long userId, AssistantCommands.StartConversation command) {
        requireEnabled();
        if (command == null || command.postId() <= 0) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
        String selectedText = normalize(command.selectedText());
        String question = normalize(command.question());
        validateInput(selectedText, question);

        // 帖子不存在 / 已下架 / 无可见性时沿用 forum 的 3001，助手不另造口径
        PostDetail detail = forumQueryApplicationService.postDetail(command.postId(), userId);
        moderationApplicationService.ensureAllowed(question);

        AssistantConversation draft = AssistantConversation.start(userId, command.postId(),
                        detail.title(), detail.boardName(), selectedText)
                .withUserMessage(question);
        return prepare(userId, draft);
    }

    public AssistantStreamHandle followUp(long userId, AssistantCommands.FollowUp command) {
        requireEnabled();
        if (command == null || command.conversationId() == null || command.conversationId().isBlank()) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
        String question = normalize(command.question());
        if (question.isEmpty()) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
        if (question.length() > props.getAssistant().getMaxQuestionChars()) {
            throw new ApiException(ResultCode.ASSISTANT_INPUT_TOO_LONG);
        }

        AssistantConversation existing = conversationStore.find(command.conversationId())
                .filter(conversation -> conversation.userId() == userId)
                .orElseThrow(() -> new ApiException(ResultCode.ASSISTANT_CONVERSATION_NOT_FOUND));
        if (existing.reachedUserTurnLimit(props.getAssistant().getMaxUserTurns())) {
            throw new ApiException(ResultCode.ASSISTANT_TURN_LIMIT);
        }
        moderationApplicationService.ensureAllowed(question);

        return prepare(userId, existing.withUserMessage(question));
    }

    private void requireEnabled() {
        if (!props.getAssistant().isEnabled()) {
            throw new ApiException(ResultCode.ASSISTANT_DISABLED);
        }
    }

    private void validateInput(String selectedText, String question) {
        if (selectedText.isEmpty() || question.isEmpty()) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
        if (selectedText.length() > props.getAssistant().getMaxSelectedChars()
                || question.length() > props.getAssistant().getMaxQuestionChars()) {
            throw new ApiException(ResultCode.ASSISTANT_INPUT_TOO_LONG);
        }
    }

    private String normalize(String text) {
        return text == null ? "" : text.strip();
    }

    private AssistantStreamHandle prepare(long userId, AssistantConversation draft) {
        AssistantUsageGateway.UsageDecision decision = acquire(userId);
        return new Handle(userId, draft, decision.remainingToday(), decision.leaseToken());
    }

    private AssistantUsageGateway.UsageDecision acquire(long userId) {
        AppProperties.Assistant config = props.getAssistant();
        try {
            AssistantUsageGateway.UsageDecision decision = usageGateway.acquire(userId,
                    config.getDailyLimit(), config.getPerMinuteLimit(), ZoneId.of(config.getQuotaZone()),
                    Duration.ofSeconds(Math.max(60L, config.getSseTimeoutSeconds() + 30L)));
            if (decision.allowed()) {
                return decision;
            }
            ResultCode code = switch (decision.reason()) {
                case RATE_LIMITED -> ResultCode.ASSISTANT_RATE_LIMITED;
                case QUOTA_EXCEEDED -> ResultCode.ASSISTANT_QUOTA_EXCEEDED;
                case BUSY -> ResultCode.ASSISTANT_BUSY;
            };
            throw new ApiException(code);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("assistant usage acquire failed: {}", e.getClass().getSimpleName());
            throw new ApiException(ResultCode.ASSISTANT_UNAVAILABLE);
        }
    }

    private Duration sessionTtl() {
        return Duration.ofMinutes(Math.max(1, props.getAssistant().getSessionTtlMinutes()));
    }

    private final class Handle implements AssistantStreamHandle {

        private final long userId;
        private final AssistantConversation draft;
        private final int remainingToday;
        private final String leaseToken;
        private final ChatStreamControl streamControl = new ChatStreamControl();
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private volatile Future<?> future;

        private Handle(long userId, AssistantConversation draft, int remainingToday, String leaseToken) {
            this.userId = userId;
            this.draft = draft;
            this.remainingToday = remainingToday;
            this.leaseToken = leaseToken;
        }

        @Override
        public void start(AssistantStreamObserver observer) {
            try {
                future = assistantStreamExecutor.submit(() -> run(observer));
            } catch (RejectedExecutionException e) {
                releaseQuietly();
                throw new ApiException(ResultCode.ASSISTANT_UNAVAILABLE);
            }
        }

        @Override
        public void cancel() {
            cancelled.set(true);
            streamControl.cancel();
            Future<?> current = future;
            if (current != null) {
                current.cancel(true);
            }
        }

        private void run(AssistantStreamObserver observer) {
            try {
                if (cancelled.get()) {
                    return;
                }
                observer.onMeta(draft.id(), remainingToday);
                AppProperties.Assistant config = props.getAssistant();
                ChatModelGateway.ChatRequest request = promptBuilder.build(draft,
                        config.getMaxOutputTokens(), config.getTemperature());
                StringBuilder answer = new StringBuilder();
                AtomicReference<ChatCompletion> completion = new AtomicReference<>();

                chatModelGateway.stream(request, text -> {
                    if (cancelled.get()) {
                        return;
                    }
                    answer.append(text);
                    observer.onDelta(text);
                }, completion::set, streamControl);

                if (cancelled.get()) {
                    return;
                }
                String raw = answer.toString();
                if (raw.isBlank()) {
                    observer.onError(ResultCode.ASSISTANT_UNAVAILABLE);
                    return;
                }

                boolean blocked = false;
                String finalRaw = raw;
                if (config.isModerateOutput()) {
                    try {
                        moderationApplicationService.ensureAllowed(raw);
                    } catch (ApiException e) {
                        if (e.getCode() == ResultCode.MODERATION_BLOCKED) {
                            finalRaw = BLOCKED_ANSWER;
                            blocked = true;
                        } else {
                            // 机审服务不可用时保留已生成的私密回答；它不是公开发布，不把临时故障升级成用户损失
                            log.warn("assistant output moderation unavailable: {}", e.getCode().getCode());
                        }
                    }
                }

                String html = markdownRenderer.renderForAssistant(finalRaw);
                try {
                    conversationStore.save(draft.withAssistantMessage(finalRaw), sessionTtl());
                } catch (Exception e) {
                    // 回答已经流出；保存失败只影响追问，不让本轮变成错误
                    log.warn("assistant conversation save failed: {}", e.getClass().getSimpleName());
                }

                ChatCompletion finished = completion.get();
                String finishReason = blocked ? "blocked"
                        : finished == null || finished.finishReason() == null ? "stop" : finished.finishReason();
                observer.onDone(new AssistantResults.AssistantDone(finalRaw, html, finishReason, blocked,
                        finished == null ? null : finished.promptTokens(),
                        finished == null ? null : finished.completionTokens()));
            } catch (ApiException e) {
                if (!cancelled.get()) {
                    observer.onError(e.getCode());
                }
            } catch (ChatModelException e) {
                if (!cancelled.get()) {
                    log.warn("assistant model failed: {}", e.getMessage());
                    observer.onError(ResultCode.ASSISTANT_UNAVAILABLE);
                }
            } catch (Exception e) {
                if (!cancelled.get()) {
                    log.warn("assistant stream failed: {}", e.getClass().getSimpleName());
                    observer.onError(ResultCode.ASSISTANT_UNAVAILABLE);
                }
            } finally {
                releaseQuietly();
            }
        }

        private void releaseQuietly() {
            try {
                usageGateway.release(userId, leaseToken);
            } catch (Exception e) {
                log.warn("assistant usage release failed: {}", e.getClass().getSimpleName());
            }
        }
    }
}
