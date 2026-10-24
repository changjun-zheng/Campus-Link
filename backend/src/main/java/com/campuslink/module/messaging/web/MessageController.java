package com.campuslink.module.messaging.web;

import com.campuslink.common.result.ApiResponse;
import com.campuslink.common.result.ErrorCodes;
import com.campuslink.common.result.ResultCode;
import com.campuslink.common.web.ApiDocs;
import com.campuslink.common.web.CurrentUser;
import com.campuslink.module.messaging.application.MessagingApplicationService;
import com.campuslink.module.messaging.application.cmd.MessagingResults.ConversationPage;
import com.campuslink.module.messaging.application.cmd.MessagingResults.MarkReadResult;
import com.campuslink.module.messaging.application.cmd.MessagingResults.MessageItem;
import com.campuslink.module.messaging.application.cmd.MessagingResults.MessagePage;
import com.campuslink.module.messaging.application.cmd.SendMessageCommand;
import com.campuslink.module.messaging.web.vo.ConversationVo;
import com.campuslink.module.messaging.web.vo.MessageVo;
import com.campuslink.module.messaging.web.vo.PageVo;
import com.campuslink.module.messaging.web.vo.UnreadCountVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 私信接口（F-SOC-003 / CR-135）：五个端点全部是**本人私有数据**，一律受保护。
 *
 * <p>鉴权约定同其他上下文（N-4 已闭环，CR-028）：声明 {@link SecurityRequirement} 且真的调用
 * {@link CurrentUser#requireId}，由 {@code ArchitectureGuardTest} G7 机器校验；
 * 身份只从令牌取，不接受入参代传——{@code peerId} 是对方账号 id，不是"我"。
 *
 * <p>⚠️ 方法名刻意避开既有 operationId：{@code markRead} / {@code unreadCount} 已被
 * {@code NotificationController} 占用，同名会让 springdoc 把**既有**端点改成 {@code markRead_1}
 * （CR-130 / CR-132 两次实测的撞车模式）。
 */
@Tag(name = "message", description = "私信")
@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessagingApplicationService messagingApplicationService;

    @Operation(summary = "我的会话列表（需登录，分页，按最后一条消息时间倒序）：条目含对方昵称 / 头像、最后消息前 50 字与未读数")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ ResultCode.NOT_LOGGED_IN })
    @GetMapping("/conversations")
    public ApiResponse<PageVo<ConversationVo>> listConversations(Authentication authentication,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        long userId = CurrentUser.requireId(authentication);
        ConversationPage result = messagingApplicationService.listConversations(userId, page, size);
        return ApiResponse.ok(new PageVo<>(result.list().stream().map(ConversationVo::from).toList(),
                result.total(), result.page(), result.size()));
    }

    @Operation(summary = "未读私信总数（需登录）：顶栏角标")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ ResultCode.NOT_LOGGED_IN })
    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountVo> unreadMessageCount(Authentication authentication) {
        return ApiResponse.ok(UnreadCountVo.from(
                messagingApplicationService.unreadCount(CurrentUser.requireId(authentication))));
    }

    @Operation(summary = "与某人的消息（需登录，倒序分页，最新在前）：无会话返回空页，不报错")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ ResultCode.NOT_LOGGED_IN })
    @GetMapping("/{peerId}")
    public ApiResponse<PageVo<MessageVo>> listMessages(@PathVariable("peerId") long peerId,
            Authentication authentication,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        long userId = CurrentUser.requireId(authentication);
        MessagePage result = messagingApplicationService.listMessages(userId, peerId, page, size);
        return ApiResponse.ok(new PageVo<>(result.list().stream().map(MessageVo::from).toList(),
                result.total(), result.page(), result.size()));
    }

    @Operation(summary = "发送私信（需登录）：纯文本 1~1000 字；不能发给自己，双向拉黑与机审命中均被拒")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ ResultCode.NOT_LOGGED_IN, ResultCode.CANNOT_MESSAGE_SELF, ResultCode.USER_NOT_FOUND,
            ResultCode.FORBIDDEN, ResultCode.MODERATION_BLOCKED, ResultCode.MODERATION_UNAVAILABLE })
    @PostMapping("/{peerId}")
    public ApiResponse<MessageVo> sendMessage(@PathVariable("peerId") long peerId,
            @Valid @RequestBody SendMessageCommand command,
            Authentication authentication) {
        MessageItem item = messagingApplicationService.send(
                CurrentUser.requireId(authentication), peerId, command.content());
        return ApiResponse.ok(MessageVo.from(item));
    }

    @Operation(summary = "标记与某人的会话已读（需登录，幂等）：updated 为本次新标记的条数，无未读为 0")
    @SecurityRequirement(name = ApiDocs.BEARER_AUTH)
    @ErrorCodes({ ResultCode.NOT_LOGGED_IN })
    @PostMapping("/{peerId}/read")
    public ApiResponse<MarkReadResult> markConversationRead(@PathVariable("peerId") long peerId,
            Authentication authentication) {
        return ApiResponse.ok(messagingApplicationService.markRead(
                CurrentUser.requireId(authentication), peerId));
    }
}
