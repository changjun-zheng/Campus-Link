package com.campuslink.module.assistant.web.vo;

import com.campuslink.module.assistant.application.AssistantResults;

import java.util.List;

/** 公开配置 VO（CR-200）：只暴露入口开关与限额，不含模型 / 密钥 / 上游地址。 */
public record AssistantConfigVo(boolean enabled, int dailyLimit, int maxSelectedChars, int maxQuestionChars,
        String privacyNoticeVersion, List<String> quickPrompts) {

    public AssistantConfigVo {
        quickPrompts = List.copyOf(quickPrompts);
    }

    public static AssistantConfigVo from(AssistantResults.AssistantConfig config) {
        return new AssistantConfigVo(config.enabled(), config.dailyLimit(), config.maxSelectedChars(),
                config.maxQuestionChars(), config.privacyNoticeVersion(), config.quickPrompts());
    }
}
