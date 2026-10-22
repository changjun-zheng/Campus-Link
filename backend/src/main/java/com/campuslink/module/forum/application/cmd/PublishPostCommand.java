package com.campuslink.module.forum.application.cmd;

import com.campuslink.common.validation.ContentLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 发帖入参（web 层直接以 Command 为请求体，jakarta 校验注解随命令走）；tags 为 CR-083 标签闭环入参（0~5 个、单个 ≤32 字，重复与空白由用例规范化） */
public record PublishPostCommand(
        @NotBlank(message = "版块不能为空") String boardCode,
        @NotBlank(message = "标题不能为空") @Size(max = 100, message = "标题最长 100 字") String title,
        @NotBlank(message = "正文不能为空")
        @Size(max = ContentLimits.MAX_CHARS, message = ContentLimits.MAX_CHARS_MESSAGE) String contentMd,
        @Size(max = 5, message = "标签最多 5 个") List<@Size(max = 32, message = "单个标签最长 32 字") String> tags) {
}
