package com.campuslink.module.forum.application;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.forum.domain.gateway.ModerationGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 文本机审入口（F-SAFE-001 / CR-122）：把 {@link ModerationGateway} 的 fail-closed 口径
 * 收敛在一处，供 forum 内部与跨上下文（account 的昵称 / 签名）共用。
 *
 * <p>与发帖 / 回帖同口径：命中 → {@code 5001 / 422}；网关任何异常 → {@code 5002 / 503}
 * （宁可暂时关写入入口，不可跳过审核）。空值 / 空白跳过——"未提供"不是内容。
 *
 * <p>放在 application 层而不是 domain：跨上下文（account）只允许调用对方的 application 服务
 * （ADR-012 / 守护测试 G4），而端口与适配器仍留在 forum 的 domain / infrastructure。
 */
@Service
@RequiredArgsConstructor
public class ModerationApplicationService {

    private final ModerationGateway moderationGateway;

    public void ensureAllowed(String... texts) {
        for (String text : texts) {
            if (text == null || text.isBlank()) {
                continue;
            }
            try {
                if (!moderationGateway.moderate(text).pass()) {
                    throw new ApiException(ResultCode.MODERATION_BLOCKED);
                }
            } catch (ApiException e) {
                throw e;
            } catch (Exception e) {
                throw new ApiException(ResultCode.MODERATION_UNAVAILABLE);
            }
        }
    }
}
