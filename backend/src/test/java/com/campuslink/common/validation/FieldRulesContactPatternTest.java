package com.campuslink.common.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 引流 / 联系方式禁则（O4 / CR-119）：只挡显式网址与站外联系方式，不误伤正常昵称 / 签名。
 */
class FieldRulesContactPatternTest {

    private static final Pattern CONTACT = Pattern.compile(FieldRules.CONTACT_BANNED_PATTERN);

    private static boolean allowed(String value) {
        return CONTACT.matcher(value).matches();
    }

    @Test
    @DisplayName("正常昵称 / 签名放行")
    void normalTextAllowed() {
        assertThat(allowed("张三")).isTrue();
        assertThat(allowed("2024 级 软件工程")).isTrue();
        assertThat(allowed("喜欢写 Rust 和 TypeScript")).isTrue();
        assertThat(allowed("QQ 音乐重度用户")).isTrue();
    }

    @Test
    @DisplayName("网址与站外联系方式拦下")
    void contactTextBlocked() {
        assertThat(allowed("看这里 https://example.com")).isFalse();
        assertThat(allowed("www.mysite.cn")).isFalse();
        assertThat(allowed("加微信 abc123")).isFalse();
        assertThat(allowed("VX：abc")).isFalse();
        assertThat(allowed("加V聊")).isFalse();
        assertThat(allowed("QQ群 12345")).isFalse();
        assertThat(allowed("telegram @someone")).isFalse();
        assertThat(allowed("t.me/abc")).isFalse();
    }
}
