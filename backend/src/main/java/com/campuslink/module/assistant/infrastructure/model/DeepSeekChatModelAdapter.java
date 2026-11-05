package com.campuslink.module.assistant.infrastructure.model;

import com.campuslink.config.AppProperties;
import com.campuslink.module.assistant.domain.gateway.ChatModelException;
import com.campuslink.module.assistant.domain.gateway.ChatModelGateway;
import com.campuslink.module.assistant.domain.gateway.ChatStreamControl;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * DeepSeek / OpenAI 兼容流式适配器（CR-200）。
 *
 * <p>只用 JDK {@link HttpClient}，不引入 SDK；请求体含选中内容与问题，**不记录**。
 * 响应按 {@code data: {...}} 逐行解析，忽略注释与非 data 行；取消时关闭响应体，
 * 读取循环在下一行检查取消标记后退出，不再继续消耗 token。
 */
@Component
public class DeepSeekChatModelAdapter implements ChatModelGateway {

    private final AppProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DeepSeekChatModelAdapter(AppProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        AppProperties.Assistant config = props.getAssistant();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, config.getConnectTimeoutSeconds())))
                .build();
    }

    @Override
    public void stream(ChatRequest request, Consumer<String> onDelta,
            Consumer<ChatCompletion> onComplete, ChatStreamControl control) {
        AppProperties.Assistant config = props.getAssistant();
        if (!StringUtils.hasText(config.getApiKey())) {
            throw new ChatModelException("model api key missing");
        }
        String json;
        try {
            json = buildBody(request, config);
        } catch (RuntimeException e) {
            throw new ChatModelException("model request serialization failed", e);
        }
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(endpoint(config.getBaseUrl())))
                .timeout(Duration.ofSeconds(Math.max(1, config.getRequestTimeoutSeconds())))
                .header("Authorization", "Bearer " + config.getApiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<InputStream> response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                control.attach(body);
                if (response.statusCode() != 200) {
                    throw new ChatModelException("model HTTP " + response.statusCode());
                }
                readEvents(body, onDelta, onComplete, control);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (!control.isCancelled()) {
                throw new ChatModelException("model request interrupted", e);
            }
        } catch (IOException e) {
            if (!control.isCancelled()) {
                throw new ChatModelException("model stream read failed", e);
            }
        }
    }

    private String buildBody(ChatRequest request, AppProperties.Assistant config) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", request.systemPrompt()));
        for (ChatMessage message : request.messages()) {
            messages.add(Map.of("role", message.role(), "content", message.content()));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.getModel());
        body.put("messages", messages);
        body.put("stream", true);
        body.put("max_tokens", request.maxTokens());
        body.put("temperature", request.temperature());
        return objectMapper.writeValueAsString(body);
    }

    private void readEvents(InputStream body, Consumer<String> onDelta,
            Consumer<ChatCompletion> onComplete, ChatStreamControl control) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8));
        String finishReason = null;
        Integer promptTokens = null;
        Integer completionTokens = null;

        String line;
        while ((line = reader.readLine()) != null) {
            if (control.isCancelled()) {
                return;
            }
            if (line.isBlank() || line.startsWith(":")) {
                continue;
            }
            if (!line.startsWith("data:")) {
                continue;
            }
            String data = line.substring("data:".length()).trim();
            if ("[DONE]".equals(data)) {
                break;
            }
            JsonNode event = objectMapper.readTree(data);
            if (event.has("error")) {
                throw new ChatModelException("model returned an error event");
            }
            JsonNode choices = event.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode choice = choices.get(0);
                String delta = choice.path("delta").path("content").asText("");
                if (!delta.isEmpty()) {
                    onDelta.accept(delta);
                }
                String reason = choice.path("finish_reason").asText("");
                if (!reason.isEmpty()) {
                    finishReason = reason;
                }
            }
            JsonNode usage = event.path("usage");
            if (usage.isObject()) {
                if (usage.has("prompt_tokens")) {
                    promptTokens = usage.get("prompt_tokens").asInt();
                }
                if (usage.has("completion_tokens")) {
                    completionTokens = usage.get("completion_tokens").asInt();
                }
            }
        }
        if (!control.isCancelled()) {
            onComplete.accept(new ChatCompletion(finishReason, promptTokens, completionTokens));
        }
    }

    private String endpoint(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        return normalized + "/chat/completions";
    }
}
