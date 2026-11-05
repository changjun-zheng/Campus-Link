package com.campuslink.module.assistant.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 选中文本问 AI 的会话聚合（CR-200）。
 *
 * <p>会话只活在 Redis（30 分钟 TTL），不落 MySQL；这里保持不可变：每次追加消息返回新对象，
 * 模型调用失败时原对象不会被污染，下一次追问仍从上一轮成功回答继续。
 */
public final class AssistantConversation {

    private final String id;
    private final long userId;
    private final long postId;
    private final String postTitle;
    private final String boardName;
    private final String selectedText;
    private final List<AssistantMessage> messages;
    private final Instant createdAt;
    private final Instant updatedAt;

    private AssistantConversation(String id, long userId, long postId, String postTitle, String boardName,
            String selectedText, List<AssistantMessage> messages, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.postId = postId;
        this.postTitle = postTitle;
        this.boardName = boardName;
        this.selectedText = selectedText;
        this.messages = List.copyOf(messages);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static AssistantConversation start(long userId, long postId, String postTitle, String boardName,
            String selectedText) {
        Instant now = Instant.now();
        return new AssistantConversation(UUID.randomUUID().toString(), userId, postId,
                postTitle == null ? "" : postTitle, boardName == null ? "" : boardName,
                selectedText == null ? "" : selectedText, List.of(), now, now);
    }

    /** Redis 反序列化用：保留原 id / 时间戳，供追问复用。 */
    public static AssistantConversation restore(String id, long userId, long postId, String postTitle,
            String boardName, String selectedText, List<AssistantMessage> messages, Instant createdAt,
            Instant updatedAt) {
        return new AssistantConversation(id, userId, postId,
                postTitle == null ? "" : postTitle, boardName == null ? "" : boardName,
                selectedText == null ? "" : selectedText, messages, createdAt, updatedAt);
    }

    public AssistantConversation withUserMessage(String content) {
        return append(new AssistantMessage(AssistantMessage.Role.USER, content));
    }

    public AssistantConversation withAssistantMessage(String content) {
        return append(new AssistantMessage(AssistantMessage.Role.ASSISTANT, content));
    }

    private AssistantConversation append(AssistantMessage message) {
        List<AssistantMessage> next = new ArrayList<>(messages);
        next.add(message);
        return new AssistantConversation(id, userId, postId, postTitle, boardName, selectedText, next,
                createdAt, Instant.now());
    }

    /** 已成功发出的用户问题数；失败 / 被中止的轮次不落库，因此不会计入。 */
    public int userTurnCount() {
        return (int) messages.stream()
                .filter(message -> message.role() == AssistantMessage.Role.USER)
                .count();
    }

    public boolean reachedUserTurnLimit(int maxUserTurns) {
        return userTurnCount() >= maxUserTurns;
    }

    public String id() {
        return id;
    }

    public long userId() {
        return userId;
    }

    public long postId() {
        return postId;
    }

    public String postTitle() {
        return postTitle;
    }

    public String boardName() {
        return boardName;
    }

    public String selectedText() {
        return selectedText;
    }

    public List<AssistantMessage> messages() {
        return messages;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
