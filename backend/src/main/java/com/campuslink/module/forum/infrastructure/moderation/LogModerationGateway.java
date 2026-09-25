package com.campuslink.module.forum.infrastructure.moderation;

import com.campuslink.module.forum.domain.gateway.ModerationGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 机审 log 通道（CR-082，`campuslink.moderation.mode=log` 缺省）：本地决策留痕、默认放行。
 *
 * <p>**与真实服务商的边界如实登记**（实施方案 §2）：本实现不含任何远端调用——文本送审的
 * 第三方适配（阿里云 / 腾讯云，需 API 凭据与计费账号）归后续 CR；届时新增适配器实现本端口、
 * 以 `mode=aliyun` 条件装配即可，应用层与 fail-closed 语义不变。**fail-closed 在本实现上的
 * 体现**：调用方对 {@code moderate} 的任何异常一律 5002 拦发布，绝不降级为放行。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "campuslink.moderation.mode", havingValue = "log", matchIfMissing = true)
public class LogModerationGateway implements ModerationGateway {

    @Override
    public ModerationVerdict moderate(String content) {
        String brief = content == null ? "" : content.substring(0, Math.min(content.length(), 80));
        log.info("[MODERATION:log] decision=PASS len={} brief={}", content == null ? 0 : content.length(), brief);
        return ModerationVerdict.ok();
    }
}
