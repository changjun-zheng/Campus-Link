package com.campuslink.module.account.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 值对象：注册 / 登录 / 验证码共用的"邮箱**或**手机号"二合一账号渠道（CR-093 / F-011）。
 *
 * <p>{@link #parse} 据 {@link PhoneNumber#looksLike} 分流：像手机号走手机通道，其余按邮箱解析。
 * 邮箱格式门采用 HTML5 email 正则（与原 jakarta {@code @Email} 同族、宽进严出）——**存量邮箱全部兼容**，
 * 解析失败的（既非手机号又非邮箱形态）抛 {@link IllegalArgumentException}，调用方翻译为 1001 / LOGIN_FAILED。
 *
 * <p>{@link #normalized()} 是验证码存取键与 HMAC 哈希的统一输入：发送、校验、消费三处必须同键，
 * 否则会出现"发给 A、校验找 B"的分裂。
 */
public record Contact(EmailAddress email, PhoneNumber phone) {

    /** 与原 jakarta {@code @Email} 同族的宽松格式门（HTML5 email），存量邮箱全部兼容 */
    private static final Pattern EMAIL_GATE =
            Pattern.compile("^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$");

    public Contact {
        if (email == null && phone == null) {
            throw new IllegalArgumentException("contact 不能同时缺省邮箱与手机号");
        }
        if (email != null && phone != null) {
            throw new IllegalArgumentException("contact 不能同时携带邮箱与手机号");
        }
    }

    public static Contact parse(String raw) {
        Objects.requireNonNull(raw, "contact 不能为空");
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("contact 不能为空白");
        }
        if (PhoneNumber.looksLike(trimmed)) {
            return new Contact(null, PhoneNumber.of(trimmed));
        }
        if (!EMAIL_GATE.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("contact 须为邮箱或手机号");
        }
        return new Contact(EmailAddress.of(trimmed), null);
    }

    /** 规范化取值：验证码存取键与 HMAC 哈希共用此字符串 */
    public String normalized() {
        return email != null ? email.value() : phone.value();
    }

    public boolean hasEmail() {
        return email != null;
    }

    public boolean hasPhone() {
        return phone != null;
    }
}
