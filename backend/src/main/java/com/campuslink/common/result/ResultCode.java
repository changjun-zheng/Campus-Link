package com.campuslink.common.result;

import lombok.Getter;

/**
 * 统一错误码（技术方案第 5 节）：0 成功；1xxx 通用；2xxx 账号（21xx 学籍核验）；3xxx 版块与帖子；4xxx 权限；5xxx 安全与机审。
 */
@Getter
public enum ResultCode {

    OK(0, 200, "成功"),

    INVALID_PARAM(1001, 400, "参数错误"),
    METHOD_NOT_ALLOWED(1002, 405, "请求方法不支持"),
    UNSUPPORTED_MEDIA_TYPE(1003, 415, "不支持的请求内容类型"),
    ENDPOINT_NOT_FOUND(1004, 404, "接口不存在"),
    INTERNAL_ERROR(9999, 500, "系统繁忙，请稍后再试"),

    CAPTCHA_INVALID(2001, 400, "验证码错误或已过期"),
    CAPTCHA_TOO_FREQUENT(2002, 429, "验证码发送过于频繁，请稍后再试"),
    CAPTCHA_EXCEEDED(2003, 429, "验证码发送次数已达当日上限"),
    EMAIL_EXISTS(2004, 409, "该邮箱已注册"),
    LOGIN_FAILED(2005, 400, "邮箱或验证码错误"),
    USER_BANNED(2006, 403, "账号已被封禁"),
    USER_NOT_FOUND(2007, 404, "用户不存在"),
    /** 资料编辑限流（F-ACC-007e）：不复用 2002 / 2103——二者文案绑死验证码与学籍，复用等于写脏语义字典 */
    PROFILE_UPDATE_TOO_FREQUENT(2008, 429, "资料修改过于频繁，请稍后再试"),
    CANNOT_FOLLOW_SELF(2009, 400, "不能关注自己"),
    /** 他人主页 IP 限流（GET /users/{id} 防批量枚举）：不复用 2008 / 2103——限流维度与文案都不同，复用等于写脏语义字典 */
    PROFILE_VIEW_RATE_LIMITED(2010, 429, "访问过于频繁，请稍后再试"),
    ACCOUNT_EXPIRED(2011, 403, "账号已注销"),
    REPORT_DUPLICATED(2012, 429, "24 小时内已举报过该内容"),
    ACCOUNT_DEACTIVATED(2013, 403, "账号注销中，请先撤销注销"),
    PHONE_EXISTS(2014, 409, "该手机号已注册"),

    STUDENT_VERIFY_FAILED(2101, 400, "学籍信息校验未通过"),
    VERIFY_TICKET_INVALID(2102, 400, "核验票据无效或已过期，请重新核验"),
    VERIFY_RATE_LIMITED(2103, 429, "尝试过于频繁，请 1 小时后再试"),

    NOT_FOUND(3001, 404, "资源不存在"),
    NOT_A_QUESTION(3002, 400, "仅技术问答帖可采纳最佳答案"),
    CANNOT_ACCEPT_OWN_REPLY(3003, 400, "不能采纳自己的回复"),
    NOT_LOGGED_IN(4001, 401, "未登录"),
    FORBIDDEN(4002, 403, "无权限"),

    MODERATION_BLOCKED(5001, 422, "内容未通过安全审核"),
    MODERATION_UNAVAILABLE(5002, 503, "内容审核服务暂不可用，发布已临时关闭");

    private final int code;
    private final int httpStatus;
    private final String message;

    ResultCode(int code, int httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
