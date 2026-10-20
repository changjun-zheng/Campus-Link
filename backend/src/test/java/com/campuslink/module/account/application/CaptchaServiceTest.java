package com.campuslink.module.account.application;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.config.AppProperties;
import com.campuslink.module.account.domain.gateway.CaptchaStore;
import com.campuslink.module.account.domain.gateway.CodeSender;
import com.campuslink.module.account.domain.gateway.SensitiveCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 验证码用例 {@link CaptchaService} 的限流与存取键口径（F-ACC-001 / CR-093）。
 *
 * <p>守三件事：① 两道限流的**先后顺序**——重发间隔不满足时连当日计数都不该自增，
 * 否则用户被"发送过于频繁"拦下的每一次都在消耗自己的日额度；② 日上限判定是 {@code >} 而不是 {@code >=}
 * （第 10 次仍是合法发送）；③ 发送 / 校验 / 消费三处的键**同源**（规范化 contact 的 HMAC），
 * 否则会出现"发给 A、校验找 B"的分裂——手机号与邮箱两个渠道都从同一条路径解析。
 *
 * <p>本类不覆盖 {@code warnIfFixedCodeEnabled}：它只打一行 WARN，无行为可断言
 * （固定验证码的生产禁用由 {@code ProductionSafetyGuard} 机器强制，不在这里重复）。
 */
@ExtendWith(MockitoExtension.class)
class CaptchaServiceTest {

    private static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);
    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final String EMAIL = "dev@campuslink.local";
    private static final String EMAIL_KEY = "H(dev@campuslink.local)";

    @Mock
    private CaptchaStore captchaStore;
    @Mock
    private CodeSender codeSender;
    @Mock
    private SensitiveCodec codec;

    private AppProperties props;
    private CaptchaService service;

    @BeforeEach
    void setUp() {
        props = new AppProperties();
        props.getCaptcha().setFixedCode("123456");
        service = new CaptchaService(captchaStore, codeSender, codec, props);
        org.mockito.Mockito.lenient().when(codec.hash(anyString()))
                .thenAnswer(inv -> "H(" + inv.getArgument(0) + ")");
    }

    @Test
    @DisplayName("发送：占用重发槽 + 计数 1 次，按 TTL 存固定码并投递到规范化后的目标")
    void sendStoresFixedCodeAndDispatches() {
        when(captchaStore.tryAcquireSendSlot(EMAIL_KEY, RESEND_INTERVAL)).thenReturn(true);
        when(captchaStore.incrementDailyCount(EMAIL_KEY)).thenReturn(1L);

        service.send(EMAIL);

        verify(captchaStore).saveCode(EMAIL_KEY, "123456", CODE_TTL);
        verify(codeSender).send(EMAIL, "123456");
    }

    @Test
    @DisplayName("重发间隔未到：429 / 2002，连当日计数都不自增（不消耗用户的日额度）")
    void tooFrequentDoesNotConsumeDailyQuota() {
        when(captchaStore.tryAcquireSendSlot(EMAIL_KEY, RESEND_INTERVAL)).thenReturn(false);

        assertThatThrownBy(() -> service.send(EMAIL))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.CAPTCHA_TOO_FREQUENT));

        verify(captchaStore, never()).incrementDailyCount(anyString());
        verify(captchaStore, never()).saveCode(anyString(), anyString(), any());
        verifyNoInteractions(codeSender);
    }

    @Test
    @DisplayName("日上限溢出（计数 11 > 10）：429 / 2003，不存码不投递")
    void dailyLimitExceededStopsBeforeSaving() {
        when(captchaStore.tryAcquireSendSlot(EMAIL_KEY, RESEND_INTERVAL)).thenReturn(true);
        when(captchaStore.incrementDailyCount(EMAIL_KEY)).thenReturn(11L);

        assertThatThrownBy(() -> service.send(EMAIL))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.CAPTCHA_EXCEEDED));

        verify(captchaStore, never()).saveCode(anyString(), anyString(), any());
        verifyNoInteractions(codeSender);
    }

    @Test
    @DisplayName("边界：当日第 10 次（计数 == 上限）仍是合法发送，不得被 >= 误判")
    void tenthSendOfTheDayIsStillAllowed() {
        when(captchaStore.tryAcquireSendSlot(EMAIL_KEY, RESEND_INTERVAL)).thenReturn(true);
        when(captchaStore.incrementDailyCount(EMAIL_KEY)).thenReturn(10L);

        service.send(EMAIL);

        verify(captchaStore).saveCode(eq(EMAIL_KEY), anyString(), eq(CODE_TTL));
        verify(codeSender).send(eq(EMAIL), anyString());
    }

    @Test
    @DisplayName("未配置固定码：落库与投递的是同一个随机 6 位数字（两处不得各生成一次）")
    void randomCodeIsGeneratedOnceAndShared() {
        props.getCaptcha().setFixedCode("");
        when(captchaStore.tryAcquireSendSlot(EMAIL_KEY, RESEND_INTERVAL)).thenReturn(true);
        when(captchaStore.incrementDailyCount(EMAIL_KEY)).thenReturn(1L);

        service.send(EMAIL);

        ArgumentCaptor<String> saved = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(captchaStore).saveCode(eq(EMAIL_KEY), saved.capture(), eq(CODE_TTL));
        verify(codeSender).send(eq(EMAIL), sent.capture());

        assertThat(saved.getValue()).matches("\\d{6}");
        assertThat(sent.getValue()).isEqualTo(saved.getValue());
    }

    @Test
    @DisplayName("校验：与已存码相等才 true；不等或已过期（empty）均 false")
    void verifyComparesAgainstStoredCode() {
        when(captchaStore.loadCode(EMAIL_KEY))
                .thenReturn(Optional.of("123456"), Optional.of("654321"), Optional.empty());

        assertThat(service.verify(EMAIL, "123456")).isTrue();
        assertThat(service.verify(EMAIL, "123456")).isFalse();
        assertThat(service.verify(EMAIL, "123456")).isFalse();
    }

    @Test
    @DisplayName("消费：删除的键与发送同源——手机号目标按规范化值（去首尾空白）哈希")
    void consumeUsesNormalizedKey() {
        String phoneKey = "H(13800138000)";

        service.consume(" 13800138000 ");

        verify(captchaStore).deleteCode(phoneKey);
    }
}
