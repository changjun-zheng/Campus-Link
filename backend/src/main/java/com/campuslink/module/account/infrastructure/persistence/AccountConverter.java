package com.campuslink.module.account.infrastructure.persistence;

import com.campuslink.module.account.domain.gateway.SensitiveCodec;
import com.campuslink.module.account.domain.model.Account;
import com.campuslink.module.account.domain.model.AccountRole;
import com.campuslink.module.account.domain.model.AccountStatus;
import com.campuslink.module.account.domain.model.EmailAddress;
import com.campuslink.module.account.domain.model.PhoneNumber;
import com.campuslink.module.account.domain.model.StudentId;

/**
 * 防腐层（Anti-Corruption Layer）：MyBatis-Plus DO ↔ 账号聚合互转。
 * 明文仅在转换瞬间经过 SensitiveCodec 托管为 _enc / _hash；聚合重建时按需解密还原值对象。
 */
public final class AccountConverter {

    private AccountConverter() {
    }

    public static UserDO toDo(Account account, SensitiveCodec codec) {
        UserDO d = new UserDO();
        d.setId(account.getId());
        // 手机号渠道账号（CR-093）无邮箱：email_enc/hash 留 NULL（V1 本就可空，唯一索引允许多 NULL）
        if (account.getEmail() != null) {
            d.setEmailEnc(codec.encrypt(account.getEmail().value()));
            d.setEmailHash(codec.hash(account.getEmail().value()));
        }
        if (account.getPhone() != null) {
            d.setPhoneEnc(codec.encrypt(account.getPhone().value()));
            d.setPhoneHash(codec.hash(account.getPhone().value()));
        }
        d.setNickname(account.getNickname());
        d.setAvatarUrl(account.getAvatarUrl());
        d.setSchool(account.getSchool());
        d.setMajor(account.getMajor());
        d.setGrade(account.getGrade());
        d.setBio(account.getBio());
        d.setRole(account.getRole().name());
        d.setStatus(account.getStatus().name());
        d.setVerified(account.isVerified());
        // 管理员等非学生账号无学号（users.student_id_* 可空，唯一索引允许多 NULL）
        if (account.getStudentId() != null) {
            d.setStudentIdEnc(codec.encrypt(account.getStudentId().value()));
            d.setStudentIdHash(codec.hash(account.getStudentId().value()));
        }
        d.setAnonymized(account.isAnonymized());
        d.setDeleteAt(account.getDeleteAt());
        d.setCreatedAt(account.getCreatedAt());
        d.setUpdatedAt(account.getUpdatedAt());
        return d;
    }

    public static Account toDomain(UserDO d, SensitiveCodec codec) {
        // 匿名化（CR-082）后 email_enc / student_id_enc 为 NULL：值对象必填，回落合成占位值——
        // 该账号已不可登录、不再参与任何敏感比对，占位值仅满足聚合重建的类型约束。
        // 手机号渠道账号（CR-093）的 email_enc 同样为 NULL，占位值不参与登录（其登录走 phone_hash）。
        EmailAddress email = d.getEmailEnc() == null
                ? EmailAddress.of("deactivated-" + d.getId() + "@invalid.local")
                : EmailAddress.of(codec.decrypt(d.getEmailEnc()));
        PhoneNumber phone = d.getPhoneEnc() == null
                ? null
                : PhoneNumber.of(codec.decrypt(d.getPhoneEnc()));
        return Account.rehydrate(
                d.getId(),
                email,
                phone,
                d.getStudentIdEnc() == null ? null : StudentId.of(codec.decrypt(d.getStudentIdEnc())),
                d.getNickname(),
                d.getAvatarUrl(),
                d.getSchool(),
                d.getMajor(),
                d.getGrade(),
                d.getBio(),
                AccountRole.valueOf(d.getRole()),
                AccountStatus.valueOf(d.getStatus()),
                Boolean.TRUE.equals(d.getVerified()),
                Boolean.TRUE.equals(d.getAnonymized()),
                d.getDeleteAt(),
                d.getCreatedAt(),
                d.getUpdatedAt());
    }
}
