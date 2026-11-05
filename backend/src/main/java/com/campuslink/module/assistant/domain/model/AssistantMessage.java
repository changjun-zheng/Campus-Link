package com.campuslink.module.assistant.domain.model;

/**
 * 一条 AI 对话消息（CR-200）。只保留角色与文本；时间戳由会话聚合统一维护，避免每条消息各存一份。
 */
public record AssistantMessage(Role role, String content) {

    public enum Role {
        USER,
        ASSISTANT
    }

    public AssistantMessage {
        if (role == null) {
            throw new IllegalArgumentException("role 不能为空");
        }
        if (content == null) {
            throw new IllegalArgumentException("content 不能为空");
        }
    }
}
