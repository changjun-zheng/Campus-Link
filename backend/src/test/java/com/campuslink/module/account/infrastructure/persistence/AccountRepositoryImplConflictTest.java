package com.campuslink.module.account.infrastructure.persistence;

import com.campuslink.module.account.domain.exception.AccountConflictException;
import com.campuslink.module.account.domain.gateway.SensitiveCodec;
import com.campuslink.module.account.domain.model.Account;
import com.campuslink.module.account.domain.model.EmailAddress;
import com.campuslink.module.account.domain.model.StudentId;
import com.campuslink.module.account.infrastructure.persistence.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 仓储适配器的唯一键冲突翻译测试：把数据库约束冲突翻译为领域异常（端口约定），
 * 使应用层不依赖 JDBC / MyBatis 异常类型。识别依据是驱动消息中的唯一索引名。
 *
 * <p>消息格式取自实测：{@code Duplicate entry '<hash>' for key 'users.uk_users_student_id_hash'}。
 */
@ExtendWith(MockitoExtension.class)
class AccountRepositoryImplConflictTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private SensitiveCodec codec;

    private AccountRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new AccountRepositoryImpl(userMapper, codec);
        when(codec.encrypt(anyString())).thenReturn("enc");
        when(codec.hash(anyString())).thenReturn("hash");
    }

    private static Account newAccount() {
        return Account.registered(EmailAddress.of("someone@example.com"), StudentId.of("249971346"), "张三");
    }

    /** 模拟 MySQL 驱动抛出的唯一键冲突消息 */
    private static DuplicateKeyException duplicateOn(String indexName) {
        return new DuplicateKeyException(
                "### Error updating database.  Cause: java.sql.SQLIntegrityConstraintViolationException: "
                        + "Duplicate entry 'abc123' for key 'users." + indexName + "'");
    }

    @Test
    @DisplayName("学号唯一键冲突 -> AccountConflictException(STUDENT_ID)")
    void translatesStudentIdConflict() {
        when(userMapper.insert(any(UserDO.class))).thenThrow(duplicateOn("uk_users_student_id_hash"));

        assertThatExceptionOfType(AccountConflictException.class)
                .isThrownBy(() -> repository.save(newAccount()))
                .satisfies(e -> assertThat(e.field()).isEqualTo(AccountConflictException.Field.STUDENT_ID));
    }

    @Test
    @DisplayName("邮箱唯一键冲突 -> AccountConflictException(EMAIL)")
    void translatesEmailConflict() {
        when(userMapper.insert(any(UserDO.class))).thenThrow(duplicateOn("uk_users_email_hash"));

        assertThatExceptionOfType(AccountConflictException.class)
                .isThrownBy(() -> repository.save(newAccount()))
                .satisfies(e -> assertThat(e.field()).isEqualTo(AccountConflictException.Field.EMAIL));
    }

    @Test
    @DisplayName("手机号唯一键冲突 -> AccountConflictException(PHONE)（CR-093）")
    void translatesPhoneConflict() {
        when(userMapper.insert(any(UserDO.class))).thenThrow(duplicateOn("uk_users_phone_hash"));

        assertThatExceptionOfType(AccountConflictException.class)
                .isThrownBy(() -> repository.save(newAccount()))
                .satisfies(e -> assertThat(e.field()).isEqualTo(AccountConflictException.Field.PHONE));
    }

    @Test
    @DisplayName("未预期的约束冲突 -> 原样抛出，不猜测业务语义")
    void rethrowsUnexpectedConstraintViolation() {
        // CR-093 起 phone_hash 已被翻译为 PHONE，"未预期"的示例换成将来才可能存在的约束名
        when(userMapper.insert(any(UserDO.class))).thenThrow(duplicateOn("uk_users_nickname_hash"));

        assertThatExceptionOfType(DuplicateKeyException.class)
                .isThrownBy(() -> repository.save(newAccount()));
    }
}
