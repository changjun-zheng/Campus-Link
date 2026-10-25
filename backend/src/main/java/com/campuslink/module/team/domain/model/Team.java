package com.campuslink.module.team.domain.model;

import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 组队聚合根（F-TEAM-001 / CR-137）。
 *
 * <p>状态机只有三条边：{@code RECRUITING → FULL}（审批通过后人数达上限，自动）、
 * {@code RECRUITING → CLOSED} / {@code FULL → CLOSED}（发起人关闭）；
 * **关闭不可重开**，截止日期只展示、不自动改状态（无定时任务）。
 *
 * <p>联系方式在聚合内是明文，持久化形态由仓储适配器加密为 {@code contact_enc}；
 * 是否下发由应用层按"发起人 / 已加入成员"判定。
 */
@Getter
public class Team {

    public static final int MAX_TITLE_CHARS = 60;
    public static final int MAX_DESCRIPTION_CHARS = 2000;
    public static final int MAX_TARGET_CHARS = 64;
    public static final int MAX_CONTACT_CHARS = 100;
    public static final int MIN_CAPACITY = 2;
    public static final int MAX_CAPACITY = 20;

    private final Long id;
    private final long ownerId;
    private final String title;
    private final String descriptionMd;
    private final TeamDirection direction;
    private final String target;
    private final int capacity;
    private final String contact;
    private TeamStatus status;
    private final LocalDate deadline;
    private final Instant createdAt;

    private Team(Long id, long ownerId, String title, String descriptionMd, TeamDirection direction,
                 String target, int capacity, String contact, TeamStatus status, LocalDate deadline,
                 Instant createdAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.title = title;
        this.descriptionMd = descriptionMd;
        this.direction = direction;
        this.target = target;
        this.capacity = capacity;
        this.contact = contact;
        this.status = status;
        this.deadline = deadline;
        this.createdAt = createdAt;
    }

    /** 新建招募：全部字段在此校验（web 注解是第一道，本方法是防绕过的第二道） */
    public static Team open(long ownerId, String title, String descriptionMd, TeamDirection direction,
                            String target, int capacity, String contact, LocalDate deadline, Instant createdAt) {
        if (ownerId <= 0) {
            throw new IllegalArgumentException("发起人不能为空");
        }
        String normalizedTitle = requireText(title, 2, MAX_TITLE_CHARS, "标题");
        String normalizedDescription = requireText(descriptionMd, 1, MAX_DESCRIPTION_CHARS, "简介");
        if (direction == null) {
            throw new IllegalArgumentException("方向不能为空");
        }
        String normalizedTarget = optionalText(target, MAX_TARGET_CHARS, "目标赛事");
        if (capacity < MIN_CAPACITY || capacity > MAX_CAPACITY) {
            throw new IllegalArgumentException("人数须在 " + MIN_CAPACITY + "~" + MAX_CAPACITY + " 之间");
        }
        String normalizedContact = requireText(contact, 5, MAX_CONTACT_CHARS, "联系方式");
        if (normalizedContact.contains("<") || normalizedContact.contains(">")) {
            throw new IllegalArgumentException("联系方式不能包含尖括号");
        }
        return new Team(null, ownerId, normalizedTitle, normalizedDescription, direction, normalizedTarget,
                capacity, normalizedContact, TeamStatus.RECRUITING, deadline, createdAt);
    }

    /** 仓储重建入口（infrastructure 适配器调用；{@code contact} 为解密后的明文） */
    public static Team rehydrate(Long id, long ownerId, String title, String descriptionMd,
                                 TeamDirection direction, String target, int capacity, String contact,
                                 TeamStatus status, LocalDate deadline, Instant createdAt) {
        return new Team(id, ownerId, title, descriptionMd, direction, target, capacity, contact,
                status, deadline, createdAt);
    }

    /** 招募中才允许申请 / 审批通过；FULL 与 CLOSED 的差别由应用层翻成 6003 / 6002 */
    public boolean isRecruiting() {
        return status == TeamStatus.RECRUITING;
    }

    /** 发起人关闭：幂等（已关闭再关闭不报错，也不改其他字段） */
    public void close() {
        this.status = TeamStatus.CLOSED;
    }

    /** 人数达上限：只从招募中置满，已关闭的组队不因审批而"复活" */
    public void markFull() {
        if (status == TeamStatus.RECRUITING) {
            this.status = TeamStatus.FULL;
        }
    }

    private static String requireText(String raw, int min, int max, String field) {
        String normalized = raw == null ? "" : raw.strip();
        if (normalized.length() < min || normalized.length() > max) {
            throw new IllegalArgumentException(field + "长度须在 " + min + "~" + max + " 字");
        }
        return normalized;
    }

    private static String optionalText(String raw, int max, String field) {
        String normalized = raw == null ? "" : raw.strip();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > max) {
            throw new IllegalArgumentException(field + "不能超过 " + max + " 字");
        }
        return normalized;
    }
}
