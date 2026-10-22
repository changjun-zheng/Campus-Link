package com.campuslink.common.validation;

import java.util.regex.Pattern;

/**
 * 用户自填文本字段的规则单点（F-ACC-007a / CR-071）：长度上限与字符集禁则在此定义**一次**，
 * web 侧的 jakarta 注解与账号聚合内的领域校验都引用本类。
 *
 * <p>为什么要抽这一处：昵称 {@code 2~32} 原本长在 {@code RegisterCommand} 的注解字面量上，
 * 编辑侧若另抄一份，第一次放宽上限就必然漂移（两份规则各处生效一半）。
 *
 * <p>⚠️ 字符集禁则是**服务端唯一防线**，不是第二道：昵称与签名是纯文本、不经
 * {@code MarkdownRenderer}，而基线 §4 提到的 CSP 实测全仓零实现。
 */
public final class FieldRules {

    public static final int NICKNAME_MIN_CHARS = 2;
    public static final int NICKNAME_MAX_CHARS = 32;
    /** 名称类惯例上限（{@code boards.name} / {@code tags.name} / {@code student_roster.name} 全 64），列宽 128 留余量 */
    public static final int MAJOR_MAX_CHARS = 64;
    /** 基线用词"个人签名"= 一句话，不是简介正文；档位对齐 CR-066 处置 reason ≤ 200 */
    public static final int BIO_MAX_CHARS = 200;

    /**
     * 禁则字符集（{@code @Pattern} 与领域校验共用同一份）：尖括号、控制字符、零宽字符。
     * 零宽必须**显式列出**——实测 {@code utf8mb4_0900_ai_ci} 会折叠大小写与全角，
     * 却**不折叠**零宽（{@code 'a'+U+200B+'a' <> 'aa'}），故仿冒不会被排序规则白拿掉。
     * 写成 {@code \\uXXXX} 转义而非字面字符，是为了不让不可见字符进源码、也避免它被 springdoc 抄进契约快照。
     */
    public static final String FORBIDDEN_CHARS = "<>\\p{Cntrl}\\u200B\\u200C\\u200D\\u2060\\uFEFF";
    public static final String TEXT_ALLOWED_PATTERN = "^[^" + FORBIDDEN_CHARS + "]*$";
    /**
     * 去首尾空白后须至少含一个非空白字符——真机实测取证的缺口：{@code "    "}（4 个空格）能过
     * {@code @Size(min=2)}，穿过边界后由聚合拒掉，用户看到的是笼统的"参数错误"而非"昵称不能为空白"。
     * 注册侧由 {@code @NotBlank} 顺带覆盖，只有编辑侧（null 合法）需要这一条。
     */
    public static final String NON_BLANK_PATTERN = "[\\s\\S]*\\S[\\s\\S]*";
    /**
     * 引流 / 联系方式禁则（O4 / CR-119，处置问题清单 E-013 "URL 与联系方式仍畅通"那半句）：
     * 昵称与签名不得包含网址、微信 / QQ 等站外联系方式。**只挡显式引流**，不做"疑似"判定——
     * 误伤成本高于漏判（正常帖子正文不受本规则约束，正文仍走机审通道）。
     */
    public static final String CONTACT_BANNED_PATTERN =
            "^(?!.*(?i:https?://|www\\.|微信|weixin|加微|加v|vx|qq群|扣扣群|telegram|t\\.me/)).*$";

    private static final Pattern FORBIDDEN = Pattern.compile("[" + FORBIDDEN_CHARS + "]");

    private FieldRules() {
    }

    public static boolean containsForbiddenChar(String value) {
        return value != null && FORBIDDEN.matcher(value).find();
    }

    /** 长度是否在 1~max（null 视为"未提供 / 清空"，由调用方按 PUT 语义处理，不在此判） */
    public static boolean isWithinLength(String value, int max) {
        return value == null || value.length() <= max;
    }

    /** 去首尾空白；空串归一为 null——库里存 NULL 而不是空串，重复提交同一个"清空"才幂等 */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
