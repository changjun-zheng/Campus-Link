package com.campuslink.module.assistant.web;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ApiResponse;
import com.campuslink.common.result.ResultCode;
import com.campuslink.config.AppProperties;
import com.campuslink.module.assistant.application.AssistantApplicationService;
import com.campuslink.module.assistant.application.AssistantResults;
import com.campuslink.module.assistant.application.AssistantStreamHandle;
import com.campuslink.module.assistant.web.vo.AssistantConfigVo;
import com.campuslink.module.assistant.web.vo.FollowUpRequest;
import com.campuslink.module.assistant.web.vo.StartConversationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssistantControllerTest {

    private AssistantApplicationService service;
    private AssistantController controller;
    private AssistantStreamHandle handle;

    @BeforeEach
    void setUp() {
        service = mock(AssistantApplicationService.class);
        handle = mock(AssistantStreamHandle.class);
        AppProperties props = new AppProperties();
        props.getAssistant().setSseTimeoutSeconds(120);
        controller = new AssistantController(service, props);
    }

    @Test
    @DisplayName("公开配置：返回开关、限额与快捷问题")
    void configIsPublicShape() {
        when(service.config()).thenReturn(new AssistantResults.AssistantConfig(true, 20, 2000, 500, "1",
                List.of("这段是什么意思？")));

        ApiResponse<AssistantConfigVo> response = controller.config();

        assertThat(response.code()).isZero();
        assertThat(response.data().enabled()).isTrue();
        assertThat(response.data().dailyLimit()).isEqualTo(20);
        assertThat(response.data().quickPrompts()).containsExactly("这段是什么意思？");
    }

    @Test
    @DisplayName("开启会话：匿名在业务入口即 401 / 4001")
    void startRequiresLogin() {
        assertThatThrownBy(() -> controller.start(new StartConversationRequest(1L, "选中", "问"), null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));
    }

    @Test
    @DisplayName("追问：匿名在业务入口即 401 / 4001")
    void followUpRequiresLogin() {
        assertThatThrownBy(() -> controller.followUp("c1", new FollowUpRequest("追问"), null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_LOGGED_IN));
    }

    @Test
    @DisplayName("登录后返回 SSE，并把 handle.start 接到 observer")
    void startReturnsSseAfterLogin() {
        when(service.start(anyLong(), any())).thenReturn(handle);

        SseEmitter emitter = controller.start(new StartConversationRequest(1L, "选中", "问"), user());

        assertThat(emitter).isNotNull();
    }

    @Test
    @DisplayName("登录后追问返回 SSE")
    void followUpReturnsSseAfterLogin() {
        when(service.followUp(anyLong(), any())).thenReturn(handle);

        SseEmitter emitter = controller.followUp("c1", new FollowUpRequest("追问"), user());

        assertThat(emitter).isNotNull();
    }

    private static Authentication user() {
        return new UsernamePasswordAuthenticationToken(7L, null, List.of());
    }
}
