package com.campuslink.module.assistant.web;

import com.campuslink.common.result.ApiResponse;
import com.campuslink.common.result.ErrorCodes;
import com.campuslink.common.result.ResultCode;
import com.campuslink.common.web.ApiDocs;
import com.campuslink.common.web.CurrentUser;
import com.campuslink.common.web.PublicEndpoint;
import com.campuslink.config.AppProperties;
import com.campuslink.module.assistant.application.AssistantApplicationService;
import com.campuslink.module.assistant.application.AssistantStreamHandle;
import com.campuslink.module.assistant.web.vo.AssistantConfigVo;
import com.campuslink.module.assistant.web.vo.FollowUpRequest;
import com.campuslink.module.assistant.web.vo.StartConversationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 帖子选中文本问 AI（CR-200）。
 *
 * <p>预检在 {@link AssistantApplicationService} 中同步完成，失败直接由 {@code GlobalExceptionHandler}
 * 产出普通 {@code ApiError}；成功后才创建 SSE 并开始推送。事件名：meta / delta / done / error。
 */
@Tag(name = "assistant", description = "帖子选中文本问 AI")
@RestController
@RequestMapping("/api/v1/assistant")
@RequiredArgsConstructor
public class AssistantController {

    private final AssistantApplicationService assistantApplicationService;
    private final AppProperties props;

    @Operation(summary = "AI 助手公开配置：是否启用、每日次数、选区 / 问题上限、快捷问题")
    @PublicEndpoint
    @GetMapping("/config")
    public ApiResponse<AssistantConfigVo> config() {
        return ApiResponse.ok(AssistantConfigVo.from(assistantApplicationService.config()));
    }

    @Operation(summary = "开启问 AI（需登录，SSE）：event=meta/delta/done/error；回答仅提问者可见，不写入楼层")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
            description = "SSE 事件流（text/event-stream）",
            content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE, schema = @Schema(type = "string")))
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ ResultCode.NOT_LOGGED_IN, ResultCode.INVALID_PARAM, ResultCode.NOT_FOUND,
            ResultCode.ASSISTANT_DISABLED, ResultCode.ASSISTANT_QUOTA_EXCEEDED,
            ResultCode.ASSISTANT_RATE_LIMITED, ResultCode.ASSISTANT_INPUT_TOO_LONG,
            ResultCode.ASSISTANT_BUSY, ResultCode.ASSISTANT_UNAVAILABLE,
            ResultCode.MODERATION_BLOCKED, ResultCode.MODERATION_UNAVAILABLE })
    @PostMapping(value = "/conversations", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter start(@Valid @RequestBody StartConversationRequest request,
            Authentication authentication) {
        long userId = CurrentUser.requireId(authentication);
        AssistantStreamHandle handle = assistantApplicationService.start(userId, request.toCommand());
        return attach(handle);
    }

    @Operation(summary = "当前会话追问（需登录，SSE）：会话必须属于当前用户，30 分钟过期")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
            description = "SSE 事件流（text/event-stream）",
            content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE, schema = @Schema(type = "string")))
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ ResultCode.NOT_LOGGED_IN, ResultCode.INVALID_PARAM,
            ResultCode.ASSISTANT_DISABLED, ResultCode.ASSISTANT_CONVERSATION_NOT_FOUND,
            ResultCode.ASSISTANT_TURN_LIMIT, ResultCode.ASSISTANT_QUOTA_EXCEEDED,
            ResultCode.ASSISTANT_RATE_LIMITED, ResultCode.ASSISTANT_INPUT_TOO_LONG,
            ResultCode.ASSISTANT_BUSY, ResultCode.ASSISTANT_UNAVAILABLE,
            ResultCode.MODERATION_BLOCKED, ResultCode.MODERATION_UNAVAILABLE })
    @PostMapping(value = "/conversations/{conversationId}/messages",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter followUp(@PathVariable String conversationId,
            @Valid @RequestBody FollowUpRequest request, Authentication authentication) {
        long userId = CurrentUser.requireId(authentication);
        AssistantStreamHandle handle = assistantApplicationService.followUp(userId,
                request.toCommand(conversationId));
        return attach(handle);
    }

    private SseEmitter attach(AssistantStreamHandle handle) {
        long timeoutMs = Math.max(1, props.getAssistant().getSseTimeoutSeconds()) * 1000L;
        SseEmitter emitter = new SseEmitter(timeoutMs);
        SseAssistantObserver observer = new SseAssistantObserver(emitter);
        emitter.onCompletion(handle::cancel);
        emitter.onError(error -> handle.cancel());
        emitter.onTimeout(() -> {
            handle.cancel();
            observer.onError(ResultCode.ASSISTANT_UNAVAILABLE);
        });
        handle.start(observer);
        return emitter;
    }
}
