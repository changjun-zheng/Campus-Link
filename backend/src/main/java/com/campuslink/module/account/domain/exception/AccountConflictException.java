package com.campuslink.module.account.domain.exception;

/**
 * 领域异常：账号唯一键冲突（邮箱或学号已被占用）。
 *
 * <p>由 {@code AccountRepository} 端口的 {@code save} 约定：**持久化适配器负责**把数据库唯一约束
 * 冲突（`DuplicateKeyException`）翻译为本异常，使应用层不必感知 MyBatis / JDBC 的异常类型（DIP）。
 *
 * <p>应用层据 {@link #field()} 决定对外提示，两者策略不同：
 * <ul>
 *   <li>{@link Field#EMAIL}——邮箱不属名册信息，可明确提示"该邮箱已注册"；</li>
 *   <li>{@link Field#STUDENT_ID}——**必须与其它学籍核验失败返回同一提示**，否则可据提示差异
 *       枚举出"哪些学号存在于名册且已被占用"（PRD F-ACC-004 防名册枚举）。</li>
 * </ul>
 */
public class AccountConflictException extends RuntimeException {

    /** 发生冲突的唯一键 */
    public enum Field {
        EMAIL, PHONE, STUDENT_ID
    }

    private final transient Field field;

    public AccountConflictException(Field field, String message, Throwable cause) {
        super(message, cause);
        this.field = field;
    }

    public Field field() {
        return field;
    }
}
