package com.campuslink.common.validation;

/**
 * 正文类内容长度上限的唯一来源（BUG-005 修复，CR-118）。
 *
 * <p><b>为什么抽这一处</b>：PRD F-FORUM-002 定义正文上限 **20,000 字**，而发帖 / 回帖 / 预览三个
 * 入口此前各写一份 `50000` 字面量——三处一起漂移了两套口径（数据库容量、机审耗时与性能预算都按
 * 20k 做的）。把上限收到一个常量后，{@code @Size} 注解、契约快照与前端文案不会再各说各话。
 *
 * <p>只放"正文"这一类；用户资料字段的长度规则仍归 {@link FieldRules}。
 */
public final class ContentLimits {

    /** 正文 / 回复 / 预览内容的最大字符数（PRD F-FORUM-002） */
    public static final int MAX_CHARS = 20_000;

    public static final String MAX_CHARS_MESSAGE = "正文最长 " + MAX_CHARS + " 字符";

    private ContentLimits() {
    }
}
