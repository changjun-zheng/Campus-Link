package com.campuslink.module.account.domain.model;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 入学年级归一化（CR-145 届徽章）：从名册 {@code grade} 原文提取首个 4 位年份数字。
 *
 * <p>名册格式以 B2 决策为准、现状不统一（开发库实测「模拟2024级」/「2023」/ NULL 混存），
 * 展示端只消费归一后的 4 位年份（如 "2024"）；提取不出（无 4 位数字）则不快照，存 null——
 * 名册侧口径变化时只改本类一处。
 *
 * <p>语义是**入学年份（级）**，不做「届 = 毕业年」换算——文案口径在方案 §7 D1 由发起人定，
 * 前端徽章只拼「{grade} 级」。
 */
public final class CohortYear {

    private static final Pattern FOUR_DIGITS = Pattern.compile("\\d{4}");

    private CohortYear() {
    }

    /** 「模拟2024级」→ "2024"；「2023」→ "2023"；null / 无 4 位数字 → null（不快照） */
    public static String normalize(String rosterGrade) {
        if (rosterGrade == null || rosterGrade.isBlank()) {
            return null;
        }
        Matcher matcher = FOUR_DIGITS.matcher(rosterGrade);
        return matcher.find() ? matcher.group() : null;
    }
}
