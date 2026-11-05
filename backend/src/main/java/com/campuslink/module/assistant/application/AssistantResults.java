package com.campuslink.module.assistant.application;

import java.util.List;

/** 问 AI 的 application 出参（CR-200）。 */
public final class AssistantResults {

    private AssistantResults() {
    }

    public record AssistantConfig(boolean enabled, int dailyLimit, int maxSelectedChars, int maxQuestionChars,
            String privacyNoticeVersion, List<String> quickPrompts) {
        public AssistantConfig {
            quickPrompts = List.copyOf(quickPrompts);
        }
    }

    /**
     * 完成帧：{@code raw} 是最终 Markdown（复制用），{@code html} 是服务端净化后的 HTML（展示用）。
     * 输出被机审拦截时两者都替换为安全提示，避免前端把已流出的原文当最终结果保留。
     */
    public record AssistantDone(String raw, String html, String finishReason, boolean blocked,
            Integer promptTokens, Integer completionTokens) {
    }
}
