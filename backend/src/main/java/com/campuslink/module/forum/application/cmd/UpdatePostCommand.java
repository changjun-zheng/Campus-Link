package com.campuslink.module.forum.application.cmd;

import com.campuslink.common.validation.ContentLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 编辑帖子入参（F-FORUM-007 / CR-128）：只含可改的三样——标题、Markdown 正文、标签。
 *
 * <p>**刻意没有 boardCode**：版块决定路由与列表归属，编辑改版块会让历史链接与订阅语义漂移，
 * 属另开 CR 的独立能力；请求体里不出现这个字段，前端也就无从提交它。
 */
public record UpdatePostCommand(
        @NotBlank(message = "标题不能为空") @Size(max = 100, message = "标题最长 100 字") String title,
        @NotBlank(message = "正文不能为空")
        @Size(max = ContentLimits.MAX_CHARS, message = ContentLimits.MAX_CHARS_MESSAGE) String contentMd,
        @Size(max = 5, message = "标签最多 5 个") List<@Size(max = 32, message = "单个标签最长 32 字") String> tags) {
}
