package com.campuslink.module.team.domain.model;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

/**
 * 组队方向（F-TEAM-001 / CR-137）：固定 7 项枚举，不复用论坛的标签体系。
 *
 * <p>为什么不复用 `tags`：标签是帖子侧的自由文本 + `post_tags` 关联，组队是按方向筛选，
 * 引入自由标签会让"广场筛选"退化为 LIKE 模糊匹配，且与 CR-083 的标签闭环互相纠缠。
 */
@Getter
public enum TeamDirection {

    ALGORITHM("算法"),
    FRONTEND("前端"),
    BACKEND("后端"),
    MOBILE("移动端"),
    DATA("数据"),
    SECURITY("安全"),
    OTHER("其他");

    private final String label;

    TeamDirection(String label) {
        this.label = label;
    }

    /** 入参解析：大小写不敏感、接受前后空白；非法值返回空，由应用层翻成 1001 */
    public static Optional<TeamDirection> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.strip().toUpperCase();
        return Arrays.stream(values()).filter(v -> v.name().equals(normalized)).findFirst();
    }
}
