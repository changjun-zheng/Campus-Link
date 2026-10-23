package com.campuslink.module.account.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campuslink.module.account.domain.exception.AccountConflictException;
import com.campuslink.module.account.domain.gateway.AccountRepository;
import com.campuslink.module.account.domain.gateway.SensitiveCodec;
import com.campuslink.module.account.domain.model.Account;
import com.campuslink.module.account.domain.model.AccountStatus;
import com.campuslink.module.account.domain.model.ProfileField;
import com.campuslink.module.account.infrastructure.persistence.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 适配器：AccountRepository 端口的 MyBatis-Plus 实现（适配器模式） */
@Repository
@RequiredArgsConstructor
public class AccountRepositoryImpl implements AccountRepository {

    /** 唯一索引名取自 db/migration/V1__init_schema.sql，用于识别冲突字段 */
    private static final String UK_STUDENT_ID = "uk_users_student_id_hash";
    private static final String UK_EMAIL = "uk_users_email_hash";
    private static final String UK_PHONE = "uk_users_phone_hash";

    private final UserMapper userMapper;
    private final SensitiveCodec codec;

    @Override
    public Account save(Account account) {
        UserDO d = AccountConverter.toDo(account, codec);
        try {
            userMapper.insert(d);
        } catch (DuplicateKeyException e) {
            throw translateConflict(e);
        }
        return AccountConverter.toDomain(d, codec);
    }

    /** 端口约定：把数据库唯一键冲突翻译为领域异常，应用层据此给出业务提示（见 AccountConflictException） */
    private static RuntimeException translateConflict(DuplicateKeyException e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        if (message.contains(UK_STUDENT_ID)) {
            return new AccountConflictException(AccountConflictException.Field.STUDENT_ID, "学号已被占用", e);
        }
        if (message.contains(UK_EMAIL)) {
            return new AccountConflictException(AccountConflictException.Field.EMAIL, "邮箱已被占用", e);
        }
        if (message.contains(UK_PHONE)) {
            return new AccountConflictException(AccountConflictException.Field.PHONE, "手机号已被占用", e);
        }
        // 未预期的约束冲突：不猜测业务语义，原样抛出按系统异常处理
        return e;
    }

    /** 资料编辑（F-ACC-007a）：只 SET 变化列，绝不整行回写（端口 javadoc 写明理由） */
    @Override
    public void updateProfile(Long accountId, Account account, Set<ProfileField> changed) {
        if (changed == null || changed.isEmpty()) {
            // 幂等提交：一条 SQL 都不发，updated_at 因此不会推进（ON UPDATE CURRENT_TIMESTAMP 只被真写触发）
            return;
        }
        LambdaUpdateWrapper<UserDO> update = new LambdaUpdateWrapper<UserDO>().eq(UserDO::getId, accountId);
        // 逐列判定：SET 列表恰好等于变化列，role / status / email_* / verified 永不出现在这里
        if (changed.contains(ProfileField.NICKNAME)) {
            update.set(UserDO::getNickname, account.getNickname());
        }
        if (changed.contains(ProfileField.MAJOR)) {
            update.set(UserDO::getMajor, account.getMajor());
        }
        if (changed.contains(ProfileField.BIO)) {
            update.set(UserDO::getBio, account.getBio());
        }
        userMapper.update(null, update);
    }

    @Override
    public boolean existsByEmailHash(String emailHash) {
        return userMapper.selectCount(new LambdaQueryWrapper<UserDO>()
                .eq(UserDO::getEmailHash, emailHash)) > 0;
    }

    @Override
    public boolean existsByPhoneHash(String phoneHash) {
        return userMapper.selectCount(new LambdaQueryWrapper<UserDO>()
                .eq(UserDO::getPhoneHash, phoneHash)) > 0;
    }

    @Override
    public Optional<Account> findByEmailHash(String emailHash) {
        return Optional.ofNullable(userMapper.selectOne(new LambdaQueryWrapper<UserDO>()
                        .eq(UserDO::getEmailHash, emailHash)))
                .map(d -> AccountConverter.toDomain(d, codec));
    }

    @Override
    public Optional<Account> findByPhoneHash(String phoneHash) {
        return Optional.ofNullable(userMapper.selectOne(new LambdaQueryWrapper<UserDO>()
                        .eq(UserDO::getPhoneHash, phoneHash)))
                .map(d -> AccountConverter.toDomain(d, codec));
    }

    @Override
    public Optional<Account> findById(Long id) {
        return Optional.ofNullable(userMapper.selectById(id))
                .map(d -> AccountConverter.toDomain(d, codec));
    }

    @Override
    public List<Account> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return userMapper.selectBatchIds(ids).stream()
                .map(d -> AccountConverter.toDomain(d, codec))
                .toList();
    }

    @Override
    public void updateStatus(Long accountId, AccountStatus status, Instant deleteAt) {
        userMapper.update(null, new LambdaUpdateWrapper<UserDO>()
                .eq(UserDO::getId, accountId)
                .set(UserDO::getStatus, status.name())
                .set(UserDO::getDeleteAt, deleteAt));
    }

    @Override
    public void anonymize(Long accountId, String placeholderNickname) {
        userMapper.update(null, new LambdaUpdateWrapper<UserDO>()
                .eq(UserDO::getId, accountId)
                .set(UserDO::getNickname, placeholderNickname)
                .set(UserDO::getEmailEnc, null)
                .set(UserDO::getEmailHash, null)
                .set(UserDO::getPhoneEnc, null)
                .set(UserDO::getPhoneHash, null)
                .set(UserDO::getStudentIdEnc, null)
                .set(UserDO::getStudentIdHash, null)
                .set(UserDO::getAvatarUrl, null)
                // 展示资料同属个人信息：不清 bio / major 会让匿名化账号的主页继续泄露本人信息（CR-127）
                .set(UserDO::getBio, null)
                .set(UserDO::getMajor, null)
                .set(UserDO::getSchool, null)
                .set(UserDO::getGrade, null)
                .set(UserDO::getAnonymized, true));
    }

    @Override
    public List<Account> findExpiredDeactivations(Instant now) {
        return userMapper.selectList(new LambdaQueryWrapper<UserDO>()
                .eq(UserDO::getStatus, AccountStatus.DEACTIVATED.name())
                .eq(UserDO::getAnonymized, false)
                .isNotNull(UserDO::getDeleteAt)
                .le(UserDO::getDeleteAt, now)).stream()
                .map(d -> AccountConverter.toDomain(d, codec))
                .toList();
    }
}
