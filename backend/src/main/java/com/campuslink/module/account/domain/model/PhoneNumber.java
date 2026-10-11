package com.campuslink.module.account.domain.model;

import java.util.Objects;

/**
 * 值对象：中国大陆手机号（CR-093 / F-011），规范化为去首尾空白的 11 位原样（不加密文改写）。
 * 明文仅在聚合内短暂存在，出聚合即由 SensitiveCodec 托管为 _enc / _hash——与 {@link EmailAddress} 同规矩。
 *
 * <p>格式门 {@link #PATTERN} 是注册 / 登录 / 验证码三处共用的唯一判据；非本格式的字符串
 * **不得**当手机号处理（解析方据 {@link #looksLike} 分流到邮箱通道）。
 */
public record PhoneNumber(String value) {

    /** 大陆手机号：1 开头，第二位 3-9，共 11 位数字 */
    public static final String PATTERN = "^1[3-9]\\d{9}$";

    public PhoneNumber {
        Objects.requireNonNull(value, "phone 不能为空");
        value = value.trim();
        if (!value.matches(PATTERN)) {
            throw new IllegalArgumentException("phone 格式不正确");
        }
    }

    public static PhoneNumber of(String raw) {
        return new PhoneNumber(raw);
    }

    /** 宽松预判：调用方在"邮箱或手机号"二合一输入上据它分流，避免对手机号误走邮箱解析 */
    public static boolean looksLike(String raw) {
        return raw != null && raw.trim().matches(PATTERN);
    }
}
