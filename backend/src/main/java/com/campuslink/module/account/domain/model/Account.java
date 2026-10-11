package com.campuslink.module.account.domain.model;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.common.validation.FieldRules;
import lombok.Getter;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * 账号聚合根（DDD 充血模型）：账号生命周期规则收敛在聚合内。
 * 明文邮箱 / 学号仅在创建瞬间经过聚合，持久化形态（_enc / _hash）由仓储适配器托管——
 * 聚合重建时不解密敏感字段亦可满足全部用例（见 AccountConverter）。
 */
@Getter
public class Account {

    private Long id;
    private final EmailAddress email;
    /** 手机号（CR-093 / F-011）：注册渠道为手机号的账号才有；email 与 phone **至少其一**存在 */
    private final PhoneNumber phone;
    private final StudentId studentId;
    /** 注册后由资料编辑用例改写（F-ACC-007a），故不再是 final；写入只经 {@link #updateProfile}，无公开 setter */
    private String nickname;
    private String avatarUrl;
    private String school;
    private String major;
    private String grade;
    private String bio;
    private AccountRole role;
    private AccountStatus status;
    private final boolean verified;
    private boolean anonymized;
    private Instant deleteAt;
    private final Instant createdAt;
    private Instant updatedAt;

    private Account(Long id, EmailAddress email, PhoneNumber phone, StudentId studentId, String nickname, String avatarUrl,
                    String school, String major, String grade, String bio, AccountRole role, AccountStatus status,
                    boolean verified, boolean anonymized, Instant deleteAt, Instant createdAt, Instant updatedAt) {
        if (email == null && phone == null) {
            throw new IllegalArgumentException("email 与 phone 至少其一存在");
        }
        this.id = id;
        this.email = email;
        this.phone = phone;
        this.studentId = studentId;
        this.nickname = nickname;
        this.avatarUrl = avatarUrl;
        this.school = school;
        this.major = major;
        this.grade = grade;
        this.bio = bio;
        this.role = role;
        this.status = status;
        this.verified = verified;
        this.anonymized = anonymized;
        this.deleteAt = deleteAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** 工厂方法：注册即已完成学籍核验的新账号（F-ACC-004 是注册前置，聚合不存在“未核验”态）；邮箱渠道（CR-093 前既有形态） */
    public static Account registered(EmailAddress email, StudentId studentId, String nickname) {
        return registered(email, null, studentId, nickname);
    }

    /** 工厂方法重载（CR-093 / F-011）：手机号渠道——email 与 phone **恰好其一**由调用方保证，聚合只拦双空 */
    public static Account registered(EmailAddress email, PhoneNumber phone, StudentId studentId, String nickname) {
        return new Account(null, email, phone, studentId, nickname.trim(), null, null, null, null, null,
                AccountRole.USER, AccountStatus.ACTIVE, true, false, null, null, null);
    }

    /** 仓储重建入口（infrastructure 适配器调用） */
    public static Account rehydrate(Long id, EmailAddress email, PhoneNumber phone, StudentId studentId, String nickname,
                                    String avatarUrl, String school, String major, String grade, String bio,
                                    AccountRole role, AccountStatus status, boolean verified, boolean anonymized,
                                    Instant deleteAt, Instant createdAt, Instant updatedAt) {
        return new Account(id, email, phone, studentId, nickname, avatarUrl, school, major, grade, bio,
                role, status, verified, anonymized, deleteAt, createdAt, updatedAt);
    }

    /**
     * 工厂方法：预置管理员账号（开发期种子 / 运维初始化）。
     * 管理员是运营或校方人员、不是学生，因此**无学号**、也不走学籍核验——
     * 这是 {@link #registered} 之外唯一的建号入口，role 显式给定，不依赖注册链路。
     */
    public static Account provisioned(EmailAddress email, String nickname, AccountRole role) {
        return new Account(null, email, null, null, nickname.trim(), null, null, null, null, null,
                role, AccountStatus.ACTIVE, true, false, null, null, null);
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    /**
     * 资料编辑（F-ACC-007a）：**PUT 全量语义**——传 {@code null} 的字段保持原值，传空串即清空（{@code major} /
     * {@code bio}），昵称不允许清空。长度与字符集规则引用 {@link FieldRules}，与注册侧、web 侧注解同源一处定义。
     *
     * <p>返回值是**实际发生变化的字段集合**：调用方据此只 SET 这些列（定向 UPDATE，见
     * {@code AccountRepository#updateProfile}），空集即幂等——不发 UPDATE、不推进 {@code updated_at}、不记审计。
     * 差异在聚合内算而非在应用层比对，是为了让"空串归一为 NULL"这一条规则只有一份。
     *
     * <p>不加公开 setter：本聚合的既有口径是"生命周期规则收敛在聚合内"。
     */
    public Set<ProfileField> updateProfile(String nickname, String major, String bio) {
        // 先全部校验、再统一赋值：一次提交里 nickname 合法而 bio 含尖括号时，聚合不该停在"改了一半"的状态
        String nextNickname = nickname == null ? this.nickname : requireNickname(normalize(nickname, FieldRules.NICKNAME_MAX_CHARS));
        String nextMajor = major == null ? this.major : normalize(major, FieldRules.MAJOR_MAX_CHARS);
        String nextBio = bio == null ? this.bio : normalize(bio, FieldRules.BIO_MAX_CHARS);

        Set<ProfileField> changed = EnumSet.noneOf(ProfileField.class);
        if (!Objects.equals(nextNickname, this.nickname)) {
            this.nickname = nextNickname;
            changed.add(ProfileField.NICKNAME);
        }
        if (!Objects.equals(nextMajor, this.major)) {
            this.major = nextMajor;
            changed.add(ProfileField.MAJOR);
        }
        if (!Objects.equals(nextBio, this.bio)) {
            this.bio = nextBio;
            changed.add(ProfileField.BIO);
        }
        return changed;
    }

    /** 昵称是唯一不许清空的资料列（顶栏与帖子作者位靠它认人）；空串归一后为 null，即视为"想清空" → 拒 */
    private static String requireNickname(String trimmed) {
        if (trimmed == null || trimmed.length() < FieldRules.NICKNAME_MIN_CHARS) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
        return trimmed;
    }

    /** 去首尾空白（空串归一为 null）后校验长度与字符集；不合规即 1001，与 web 侧注解同一判据 */
    private static String normalize(String raw, int maxChars) {
        String trimmed = FieldRules.trimToNull(raw);
        if (FieldRules.containsForbiddenChar(trimmed) || !FieldRules.isWithinLength(trimmed, maxChars)) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
        return trimmed;
    }
}
