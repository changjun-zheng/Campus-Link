package com.campuslink.module.forum.application.cmd;

import com.campuslink.common.validation.ContentLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 回帖入参；正文上限与发帖一致（同走 {@link ContentLimits}，防两处字面量再次漂移）。
 *
 * <p>{@code quotedReplyId} 为引用的目标楼层（F-009 / CR-085）：可空；非空时须为正整数（Bean Validation
 * 只管形状），存在性 / 可见性 / 同帖校验归应用服务（读库判定，不可见与跨帖一律 3001 不区分）。
 */
public record PublishReplyCommand(
        @NotBlank(message = "回复内容不能为空")
        @Size(max = ContentLimits.MAX_CHARS, message = ContentLimits.MAX_CHARS_MESSAGE) String contentMd,
        @Positive(message = "quotedReplyId 须为正整数") Long quotedReplyId) {
}
