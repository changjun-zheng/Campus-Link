package com.campuslink.module.messaging.application.cmd;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 发送私信入参（F-SOC-003 / CR-135）：纯文本 1~1000 字。
 *
 * <p>{@code @Size} 与 {@code Message.MAX_CONTENT_LENGTH} 同源；应用层另有 strip 后判空，
 * 防止"只含空白字符"绕过 {@code @NotBlank} 之外的第二道门。
 */
public record SendMessageCommand(@NotBlank @Size(max = 1000) String content) {
}
