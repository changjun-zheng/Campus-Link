package com.campuslink.module.forum.infrastructure.moderation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 本地词表机审（CR-118 / BUG-006）：命中即拦、大小写不敏感、空词表启动即失败。
 */
class LocalWordListModerationGatewayTest {

    @Test
    @DisplayName("未命中：放行")
    void passesCleanContent() {
        var gateway = new LocalWordListModerationGateway("违禁词,spam");
        assertThat(gateway.moderate("这是一篇正常的课程讨论").pass()).isTrue();
    }

    @Test
    @DisplayName("命中：拦下且不吐规则细节")
    void blocksMatchedContent() {
        var gateway = new LocalWordListModerationGateway("违禁词,spam");
        var verdict = gateway.moderate("这里有违禁词");
        assertThat(verdict.pass()).isFalse();
        assertThat(verdict.reason()).isEqualTo("内容包含不允许的词汇");
    }

    @Test
    @DisplayName("大小写不敏感（Locale.ROOT）")
    void matchingIsCaseInsensitive() {
        var gateway = new LocalWordListModerationGateway("Spam");
        assertThat(gateway.moderate("this is SPAM content").pass()).isFalse();
    }

    @Test
    @DisplayName("空词表：构造即失败，不允许静默放行")
    void emptyWordListFailsFast() {
        assertThatThrownBy(() -> new LocalWordListModerationGateway(""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fail-closed");
    }
}
