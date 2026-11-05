package com.campuslink.module.assistant.domain.gateway;

import java.util.List;
import java.util.function.Consumer;

/**
 * 对话模型端口（CR-200）：domain 只描述"发什么、收到什么"，OpenAI 兼容协议细节留在 infrastructure。
 */
public interface ChatModelGateway {

    /**
     * 阻塞式流式调用，由 application 放到专用线程池执行；每个增量调用 {@code onDelta}，
     * 成功时 {@code onComplete} 恰好一次，失败抛 {@link ChatModelException}。
     * 取消通过 {@link ChatStreamControl} 表达，适配器不应把取消当失败上报。
     *
     * <p>回调刻意使用 JDK {@link Consumer} 而不是自定义端口接口：application 是编排方，
     * 若在这里实现 domain 端口会违反 G5「端口实现必须在 infrastructure」。
     */
    void stream(ChatRequest request, Consumer<String> onDelta, Consumer<ChatCompletion> onComplete,
            ChatStreamControl control);

    record ChatRequest(String systemPrompt, List<ChatMessage> messages, int maxTokens, double temperature) {
        public ChatRequest {
            messages = List.copyOf(messages);
        }
    }

    /** role 只允许 user / assistant；system 由 {@link ChatRequest#systemPrompt()} 单独承载。 */
    record ChatMessage(String role, String content) {
        public ChatMessage {
            if (!"user".equals(role) && !"assistant".equals(role)) {
                throw new IllegalArgumentException("role 必须为 user 或 assistant");
            }
        }
    }

    record ChatCompletion(String finishReason, Integer promptTokens, Integer completionTokens) {
    }
}
