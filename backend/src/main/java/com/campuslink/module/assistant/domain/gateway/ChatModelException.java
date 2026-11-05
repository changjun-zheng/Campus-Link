package com.campuslink.module.assistant.domain.gateway;

/**
 * 上游模型调用失败（CR-200）。消息只描述可公开的故障类别，绝不携带密钥、请求正文或选中内容。
 */
public class ChatModelException extends RuntimeException {

    public ChatModelException(String message) {
        super(message);
    }

    public ChatModelException(String message, Throwable cause) {
        super(message, cause);
    }
}
