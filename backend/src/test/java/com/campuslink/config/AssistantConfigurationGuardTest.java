package com.campuslink.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssistantConfigurationGuardTest {

    @Test
    @DisplayName("默认关闭：不要求密钥")
    void disabledNeedsNothing() {
        AppProperties props = new AppProperties();

        assertThatCode(() -> new AssistantConfigurationGuard(props).afterPropertiesSet()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("开启但缺 DEEPSEEK_API_KEY：启动即失败")
    void enabledWithoutKeyFailsFast() {
        AppProperties props = new AppProperties();
        props.getAssistant().setEnabled(true);

        assertThatThrownBy(() -> new AssistantConfigurationGuard(props).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEEPSEEK_API_KEY");
    }

    @Test
    @DisplayName("开启且关键配置齐全：通过（开发允许 http 指向本地 mock）")
    void enabledWithConfigPasses() {
        AppProperties props = new AppProperties();
        props.getAssistant().setEnabled(true);
        props.getAssistant().setApiKey("test-key");
        props.getAssistant().setBaseUrl("http://127.0.0.1:9999");
        props.getAssistant().setModel("deepseek-chat");

        assertThatCode(() -> new AssistantConfigurationGuard(props).afterPropertiesSet()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("非法时区 / 非正限额：启动即失败")
    void invalidNumbersFailFast() {
        AppProperties props = new AppProperties();
        props.getAssistant().setEnabled(true);
        props.getAssistant().setApiKey("test-key");
        props.getAssistant().setModel("deepseek-chat");
        props.getAssistant().setQuotaZone("Not/AZone");

        assertThatThrownBy(() -> new AssistantConfigurationGuard(props).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("quota-zone");

        props.getAssistant().setQuotaZone("Asia/Shanghai");
        props.getAssistant().setDailyLimit(0);
        assertThatThrownBy(() -> new AssistantConfigurationGuard(props).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("必须为正数");
    }
}
