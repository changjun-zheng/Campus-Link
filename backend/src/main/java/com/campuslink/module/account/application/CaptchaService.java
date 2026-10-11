package com.campuslink.module.account.application;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.config.AppProperties;
import com.campuslink.module.account.domain.gateway.CaptchaStore;
import com.campuslink.module.account.domain.gateway.CodeSender;
import com.campuslink.module.account.domain.gateway.SensitiveCodec;
import com.campuslink.module.account.domain.model.Contact;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;

/**
 * 验证码用例（应用层，F-ACC-001）：限流规则（60s 重发、单号日上限）在此编排；
 * 存取经 CaptchaStore 端口、发送经 CodeSender 端口（log / mail 策略）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final CaptchaStore captchaStore;
    private final CodeSender codeSender;
    private final SensitiveCodec codec;
    private final AppProperties props;

    /** 固定验证码是开发便利，等于取消验证码防线；启动即告警，避免被静默带入非本机环境 */
    @PostConstruct
    void warnIfFixedCodeEnabled() {
        if (StringUtils.hasText(props.getCaptcha().getFixedCode())) {
            log.warn("*** 验证码已固定为常量（campuslink.captcha.fixed-code）——仅限本地联调。"
                    + "任何共享 / 预发 / 生产环境必须将该配置留空，否则等同于取消验证码防线。***");
        }
    }

    /** 发送（CR-093 / F-011）：target 为邮箱**或**手机号二合一，存取键 = 规范化 contact 的 HMAC */
    public void send(String target) {
        String targetKey = captchaKey(target);

        if (!captchaStore.tryAcquireSendSlot(targetKey,
                Duration.ofSeconds(props.getCaptcha().getResendIntervalSeconds()))) {
            throw new ApiException(ResultCode.CAPTCHA_TOO_FREQUENT);
        }
        long count = captchaStore.incrementDailyCount(targetKey);
        if (count > props.getCaptcha().getDailyLimit()) {
            throw new ApiException(ResultCode.CAPTCHA_EXCEEDED);
        }

        String code = nextCode();
        captchaStore.saveCode(targetKey, code, Duration.ofMinutes(props.getCaptcha().getTtlMinutes()));
        codeSender.send(target, code);
    }

    private String nextCode() {
        String fixed = props.getCaptcha().getFixedCode();
        return StringUtils.hasText(fixed) ? fixed : String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    public boolean verify(String target, String code) {
        return captchaStore.loadCode(captchaKey(target))
                .map(saved -> saved.equals(code))
                .orElse(false);
    }

    public void consume(String target) {
        captchaStore.deleteCode(captchaKey(target));
    }

    /** 存取键统一从"邮箱或手机号"二合一解析（CR-093）：发送 / 校验 / 消费三处同键，不因渠道分裂 */
    private String captchaKey(String target) {
        return codec.hash(Contact.parse(target).normalized());
    }
}
