package com.campuslink.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.ZoneId;

/**
 * 问 AI 助手的启动即校验（CR-200）。
 *
 * <p>与 {@link ProductionSafetyGuard} 分工：本类在**所有 profile** 下运行，只挡"开关打开但缺关键配置"
 * 这一类半开状态；生产额外要求 HTTPS 的规则仍在 prod 守卫里。默认 {@code enabled=false} 时不校验，
 * 开发期可先关着入口做前端联调。
 */
@Component
public class AssistantConfigurationGuard implements InitializingBean {

    private final AppProperties props;

    public AssistantConfigurationGuard(AppProperties props) {
        this.props = props;
    }

    @Override
    public void afterPropertiesSet() {
        AppProperties.Assistant assistant = props.getAssistant();
        if (!assistant.isEnabled()) {
            return;
        }
        if (!StringUtils.hasText(assistant.getApiKey())) {
            throw new IllegalStateException(
                    "campuslink.assistant.enabled=true 但 DEEPSEEK_API_KEY 未注入：请配置环境变量，或关闭 ASSISTANT_ENABLED");
        }
        if (!StringUtils.hasText(assistant.getBaseUrl())
                || !(assistant.getBaseUrl().startsWith("http://") || assistant.getBaseUrl().startsWith("https://"))) {
            throw new IllegalStateException("campuslink.assistant.base-url 必须是 http(s) 地址");
        }
        if (!StringUtils.hasText(assistant.getModel())) {
            throw new IllegalStateException("campuslink.assistant.model 不能为空");
        }
        if (assistant.getDailyLimit() <= 0 || assistant.getPerMinuteLimit() <= 0
                || assistant.getMaxSelectedChars() <= 0 || assistant.getMaxQuestionChars() <= 0
                || assistant.getMaxUserTurns() <= 0 || assistant.getMaxOutputTokens() <= 0
                || assistant.getSessionTtlMinutes() <= 0 || assistant.getSseTimeoutSeconds() <= 0) {
            throw new IllegalStateException("campuslink.assistant 的配额 / 长度 / 超时参数必须为正数");
        }
        try {
            ZoneId.of(assistant.getQuotaZone());
        } catch (Exception e) {
            throw new IllegalStateException("campuslink.assistant.quota-zone 不是合法时区: "
                    + assistant.getQuotaZone());
        }
    }
}
