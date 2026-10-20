package com.campuslink.module.account.infrastructure.persistence;

import com.campuslink.module.account.domain.gateway.SensitiveCodec;
import com.campuslink.module.account.domain.model.Account;
import com.campuslink.module.account.domain.model.AccountRole;
import com.campuslink.module.account.domain.model.AccountStatus;
import com.campuslink.module.account.domain.model.EmailAddress;
import com.campuslink.module.account.domain.model.PhoneNumber;
import com.campuslink.module.account.domain.model.StudentId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 账号防腐层 {@link AccountConverter} 的双向映射（CR-111 覆盖率批）。
 *
 * <p>这里守的是**可空敏感列的三条口径**，都是真机缺陷的源头：
 * ① 手机号渠道账号没有邮箱（CR-093），{@code email_enc/hash} 必须留 NULL 而不是空串；
 * ② 管理员没有学号，{@code student_id_*} 同理留 NULL（唯一索引允许多 NULL）；
 * ③ 匿名化（CR-082）后 {@code email_enc} 为 NULL，重建聚合必须给值对象一个**不可登录的**合成占位，
 *    而不是抛异常或返回 null。
 *
 * <p>本类不测加解密算法本身（那是 {@code SensitiveCodec} 适配器的职责），只断言"哪一列走了哪个端口"。
 */
@ExtendWith(MockitoExtension.class)
class AccountConverterTest {

    private static final long USER_ID = 42L;
    private static final Instant CREATED_AT = Instant.parse("2026-10-01T08:00:00Z");

    @Mock
    private SensitiveCodec codec;

    @Test
    @DisplayName("toDo：邮箱渠道只托管 email_enc / email_hash，phone_* 与 student_id_* 保持 NULL")
    void emailAccountOnlyFillsEmailColumns() {
        when(codec.encrypt(anyString())).thenAnswer(inv -> "enc:" + inv.getArgument(0));
        when(codec.hash(anyString())).thenAnswer(inv -> "hash:" + inv.getArgument(0));

        UserDO d = AccountConverter.toDo(Account.registered(
                EmailAddress.of("dev@campuslink.local"), StudentId.of("888800001"), "张三同学"), codec);

        assertThat(d.getEmailEnc()).isEqualTo("enc:dev@campuslink.local");
        assertThat(d.getEmailHash()).isEqualTo("hash:dev@campuslink.local");
        assertThat(d.getPhoneEnc()).isNull();
        assertThat(d.getPhoneHash()).isNull();
        assertThat(d.getStudentIdEnc()).isEqualTo("enc:888800001");
        assertThat(d.getStudentIdHash()).isEqualTo("hash:888800001");
        assertThat(d.getRole()).isEqualTo(AccountRole.USER.name());
        assertThat(d.getStatus()).isEqualTo(AccountStatus.ACTIVE.name());
    }

    @Test
    @DisplayName("toDo：手机号渠道账号无邮箱——email_* 不得写入，phone_* 走加密与哈希")
    void phoneAccountLeavesEmailColumnsNull() {
        when(codec.encrypt(anyString())).thenAnswer(inv -> "enc:" + inv.getArgument(0));
        when(codec.hash(anyString())).thenAnswer(inv -> "hash:" + inv.getArgument(0));

        UserDO d = AccountConverter.toDo(Account.registered(
                null, PhoneNumber.of("13800138000"), StudentId.of("888800001"), "张三同学"), codec);

        assertThat(d.getEmailEnc()).isNull();
        assertThat(d.getEmailHash()).isNull();
        assertThat(d.getPhoneEnc()).isEqualTo("enc:13800138000");
        assertThat(d.getPhoneHash()).isEqualTo("hash:13800138000");
    }

    @Test
    @DisplayName("toDo：管理员（无学号）不触发学号加密，student_id_* 保持 NULL")
    void provisionedAdminHasNoStudentIdColumns() {
        when(codec.encrypt(anyString())).thenAnswer(inv -> "enc:" + inv.getArgument(0));
        when(codec.hash(anyString())).thenAnswer(inv -> "hash:" + inv.getArgument(0));

        UserDO d = AccountConverter.toDo(
                Account.provisioned(EmailAddress.of("admin@campuslink.local"), "管理员", AccountRole.SUPERADMIN),
                codec);

        assertThat(d.getStudentIdEnc()).isNull();
        assertThat(d.getStudentIdHash()).isNull();
        verify(codec, never()).encrypt("888800001");
    }

    @Test
    @DisplayName("toDomain：NULL 的 email_enc / student_id_enc 回落合成占位与 null，verified/anonymized 的 null 视为 false")
    void toDomainNormalizesNullableColumns() {
        UserDO d = new UserDO();
        d.setId(USER_ID);
        d.setNickname("已注销用户");
        d.setRole(AccountRole.USER.name());
        d.setStatus(AccountStatus.DEACTIVATED.name());
        d.setVerified(null);
        d.setAnonymized(null);

        Account account = AccountConverter.toDomain(d, codec);

        assertThat(account.getEmail().value()).isEqualTo("deactivated-" + USER_ID + "@invalid.local");
        assertThat(account.getPhone()).isNull();
        assertThat(account.getStudentId()).isNull();
        assertThat(account.isVerified()).isFalse();
        assertThat(account.isAnonymized()).isFalse();
        verify(codec, never()).decrypt(anyString());
    }

    @Test
    @DisplayName("toDomain：加密列有值时逐个解密还原值对象，时间戳与角色状态原样带回")
    void toDomainDecryptsPresentColumns() {
        UserDO d = new UserDO();
        d.setId(USER_ID);
        d.setEmailEnc("enc-mail");
        d.setPhoneEnc("enc-phone");
        d.setStudentIdEnc("enc-sid");
        d.setNickname("张三同学");
        d.setRole(AccountRole.USER.name());
        d.setStatus(AccountStatus.ACTIVE.name());
        d.setVerified(true);
        d.setAnonymized(false);
        d.setCreatedAt(CREATED_AT);
        when(codec.decrypt("enc-mail")).thenReturn("dev@campuslink.local");
        when(codec.decrypt("enc-phone")).thenReturn("13800138000");
        when(codec.decrypt("enc-sid")).thenReturn("888800001");

        Account account = AccountConverter.toDomain(d, codec);

        assertThat(account.getEmail().value()).isEqualTo("dev@campuslink.local");
        assertThat(account.getPhone().value()).isEqualTo("13800138000");
        assertThat(account.getStudentId().value()).isEqualTo("888800001");
        assertThat(account.getRole()).isEqualTo(AccountRole.USER);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.isVerified()).isTrue();
        assertThat(account.getCreatedAt()).isEqualTo(CREATED_AT);
    }
}
