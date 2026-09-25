package com.campuslink.module.forum.domain.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 标签规范化（CR-083 标签闭环）：trim → 去空 → 去重（保序）→ 上限截断。
 * 纯静态规则，供发帖用例与单测共用；单个标签长度由 Command 校验（≤32 字）。
 */
public final class TagNames {

    private TagNames() {
    }

    public static List<String> normalize(List<String> raw, int maxCount) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String name : raw) {
            if (name == null) {
                continue;
            }
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) {
                seen.add(trimmed);
            }
        }
        List<String> out = new ArrayList<>(seen);
        return out.size() > maxCount ? out.subList(0, maxCount) : out;
    }
}
