package com.campuslink.module.forum.application;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.forum.domain.gateway.ModerationGateway;
import com.campuslink.module.forum.domain.gateway.ModerationGateway.ModerationVerdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 文本机审入口（CR-122）：与发帖 / 回帖同一 fail-closed 口径——命中 5001、网关异常 5002、
 * 空值 / 空白跳过。account 侧（昵称 / 签名）复用本入口，故这里的三态即其共用语义。
 */
@ExtendWith(MockitoExtension.class)
class ModerationApplicationServiceTest {

    @Mock
    private ModerationGateway moderationGateway;

    @InjectMocks
    private ModerationApplicationService service;

    @Test
    @DisplayName("放行：网关 pass 时不抛异常")
    void passesWhenGatewayPasses() {
        when(moderationGateway.moderate("正常昵称")).thenReturn(ModerationVerdict.ok());

        assertThatCode(() -> service.ensureAllowed("正常昵称")).doesNotThrowAnyException();

        verify(moderationGateway).moderate("正常昵称");
    }

    @Test
    @DisplayName("命中：网关 blocked → 5001 / 422")
    void blocksWhenGatewayBlocks() {
        when(moderationGateway.moderate("违规昵称"))
                .thenReturn(ModerationVerdict.blocked("内容包含不允许的词汇"));

        assertThatThrownBy(() -> service.ensureAllowed("违规昵称"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getCode())
                                .isEqualTo(ResultCode.MODERATION_BLOCKED));
    }

    @Test
    @DisplayName("fail-closed：网关异常 → 5002 / 503，绝不降级为放行")
    void failsClosedOnGatewayException() {
        when(moderationGateway.moderate(anyString())).thenThrow(new IllegalStateException("provider down"));

        assertThatThrownBy(() -> service.ensureAllowed("任意文本"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getCode())
                                .isEqualTo(ResultCode.MODERATION_UNAVAILABLE));
    }

    @Test
    @DisplayName("空值 / 空白跳过：不触达网关（未提供不是内容）")
    void skipsNullAndBlank() {
        service.ensureAllowed(null, "   ");

        verifyNoInteractions(moderationGateway);
    }
}
