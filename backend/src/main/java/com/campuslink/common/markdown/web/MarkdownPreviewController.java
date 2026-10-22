package com.campuslink.common.markdown.web;

import com.campuslink.common.markdown.MarkdownRenderer;
import com.campuslink.common.result.ApiResponse;
import com.campuslink.common.result.ErrorCodes;
import com.campuslink.common.result.ResultCode;
import com.campuslink.common.validation.ContentLimits;
import com.campuslink.common.web.PublicEndpoint;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Markdown 预览端点（F-012 / CR-087，闭环 ADR-005 的「前端预览调用同一服务端渲染接口」约定）：
 * 发帖页预览与详情页正文共用同一渲染出口 {@link MarkdownRenderer}，XSS 防线只有服务端一份。
 *
 * <p>
 * 公开端点（渲染结果已净化，无用户数据）：匿名访客在发帖页预览同样合法。
 * 正文上限与发帖一致（{@link ContentLimits#MAX_CHARS}），超限 400 / 1001。
 */
@Tag(name = "markdown", description = "Markdown 渲染（预览，F-012）")
@RestController
@RequestMapping("/api/v1/markdown")
@RequiredArgsConstructor
public class MarkdownPreviewController {

    private final MarkdownRenderer markdownRenderer;

    public record PreviewCommand(
            @NotBlank(message = "正文不能为空") @Size(max = ContentLimits.MAX_CHARS, message = ContentLimits.MAX_CHARS_MESSAGE) String contentMd) {
    }

    public record PreviewResult(String html) {
    }

    @Operation(summary = "Markdown 预览（公开）：返回服务端渲染并净化的 HTML，供发帖页实时预览")
    @PublicEndpoint
    @PostMapping("/preview")
    public ApiResponse<PreviewResult> preview(@Valid @RequestBody PreviewCommand command) {
        return ApiResponse.ok(new PreviewResult(markdownRenderer.render(command.contentMd())));
    }
}
