package com.campuslink.module.assistant.infrastructure.model;

import com.campuslink.config.AppProperties;
import com.campuslink.module.assistant.domain.gateway.ChatModelException;
import com.campuslink.module.assistant.domain.gateway.ChatModelGateway.ChatCompletion;
import com.campuslink.module.assistant.domain.gateway.ChatModelGateway.ChatMessage;
import com.campuslink.module.assistant.domain.gateway.ChatModelGateway.ChatRequest;
import com.campuslink.module.assistant.domain.gateway.ChatStreamControl;
import tools.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeepSeekChatModelAdapterTest {

    private HttpServer server;
    private DeepSeekChatModelAdapter adapter;
    private final AtomicReference<String> requestBody = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        AppProperties props = new AppProperties();
        AppProperties.Assistant config = props.getAssistant();
        config.setEnabled(true);
        config.setApiKey("test-key");
        config.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        config.setModel("test-model");
        config.setConnectTimeoutSeconds(2);
        config.setRequestTimeoutSeconds(5);
        adapter = new DeepSeekChatModelAdapter(props, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    @DisplayName("流式解析：多个 delta + finish_reason，请求体带 system / model / stream")
    void streamsDeltasAndCompletion() {
        serve(200, """
                data: {"choices":[{"delta":{"role":"assistant","content":"你"}}]}

                data: {"choices":[{"delta":{"content":"好"},"finish_reason":"stop"}]}

                data: [DONE]

                """);

        StringBuilder answer = new StringBuilder();
        AtomicReference<ChatCompletion> completion = new AtomicReference<>();
        adapter.stream(request(), (Consumer<String>) answer::append, completion::set,
                new ChatStreamControl());

        assertThat(answer.toString()).isEqualTo("你好");
        assertThat(completion.get()).isNotNull();
        assertThat(completion.get().finishReason()).isEqualTo("stop");
        assertThat(requestBody.get())
                .contains("\"model\":\"test-model\"")
                .contains("\"stream\":true")
                .contains("\"role\":\"system\"")
                .contains("\"role\":\"user\"");
    }

    @Test
    @DisplayName("HTTP 非 200：抛 ChatModelException，消息不含上游正文")
    void non200Throws() {
        serve(429, "{\"error\":{\"message\":\"rate limited\"}}");

        assertThatThrownBy(() -> adapter.stream(request(), text -> { }, completion -> { }, new ChatStreamControl()))
                .isInstanceOf(ChatModelException.class)
                .hasMessageContaining("HTTP 429")
                .hasMessageNotContaining("rate limited");
    }

    @Test
    @DisplayName("流内 error 事件：抛 ChatModelException")
    void errorEventThrows() {
        serve(200, "data: {\"error\":{\"message\":\"boom\"}}\n\n");

        assertThatThrownBy(() -> adapter.stream(request(), text -> { }, completion -> { }, new ChatStreamControl()))
                .isInstanceOf(ChatModelException.class);
    }

    private ChatRequest request() {
        return new ChatRequest("你是助手", List.of(new ChatMessage("user", "问一下")), 100, 0.1);
    }

    private void serve(int status, String body) {
        server.createContext("/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
    }
}
