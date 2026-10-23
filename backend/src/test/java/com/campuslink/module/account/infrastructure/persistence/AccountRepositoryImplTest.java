package com.campuslink.module.account.infrastructure.persistence;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.campuslink.module.account.domain.gateway.SensitiveCodec;
import com.campuslink.module.account.domain.model.Account;
import com.campuslink.module.account.domain.model.AccountRole;
import com.campuslink.module.account.domain.model.AccountStatus;
import com.campuslink.module.account.infrastructure.persistence.mapper.UserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code AccountRepositoryImpl} 的**读路径与生命周期写路径**覆盖（CR-111 覆盖率批）。
 *
 * <p>为什么与既有的两支分开：{@code AccountRepositoryImplConflictTest} 守唯一键冲突翻译，
 * {@code AccountRepositoryImplProfileUpdateTest} 守资料定向 UPDATE 的「不该写的列」；
 * 本类补其余方法——存在性判定 / 按哈希与 id 查找 / 批读短路 / 状态与注销时间写入 /
 * 匿名化抹除 / 冷静期满扫描，并把「WHERE 里到底有没有那个条件」钉住。
 *
 * <p><b>本类不假装覆盖语义</b>：断言 wrapper 只能证明"发的是这条语句"，不能证明数据库按它执行；
 * 后者归真机取证与集成测试。覆盖率是下限，不是证明。
 */
@ExtendWith(MockitoExtension.class)
class AccountRepositoryImplTest {

    private static final long USER_ID = 42L;
    private static final Instant NOW = Instant.parse("2026-10-15T08:00:00Z");

    @Mock
    private UserMapper userMapper;
    @Mock
    private SensitiveCodec codec;

    private AccountRepositoryImpl repository;

    @BeforeAll
    static void initTableInfoCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(UserMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, UserDO.class);
    }

    @BeforeEach
    void setUp() {
        repository = new AccountRepositoryImpl(userMapper, codec);
    }

    @Test
    @DisplayName("邮箱存在性：selectCount 按 email_hash 单列过滤，计数 >0 即 true")
    void existsByEmailHashFiltersOnEmailHash() {
        when(userMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        assertThat(repository.existsByEmailHash("H(mail)")).isTrue();

        LambdaQueryWrapper<UserDO> query = capturedSelectCount();
        assertThat(query.getSqlSegment()).contains("email_hash");
        assertThat(query.getParamNameValuePairs().values()).contains("H(mail)");
    }

    @Test
    @DisplayName("手机号存在性：0 条即 false，条件落在 phone_hash（不得串到 email_hash）")
    void existsByPhoneHashFiltersOnPhoneHash() {
        when(userMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        assertThat(repository.existsByPhoneHash("H(13800138000)")).isFalse();

        String sql = capturedSelectCount().getSqlSegment();
        assertThat(sql).contains("phone_hash").doesNotContain("email_hash");
    }

    @Test
    @DisplayName("按邮箱哈希查：查不到回 Optional.empty，查到经防腐层解密还原邮箱")
    void findByEmailHashMapsRowThroughConverter() {
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        assertThat(repository.findByEmailHash("H(mail)")).isEmpty();

        when(codec.decrypt("enc-mail")).thenReturn("dev@campuslink.local");
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(row(USER_ID, "enc-mail"));
        assertThat(repository.findByEmailHash("H(mail)"))
                .map(a -> a.getEmail().value())
                .hasValue("dev@campuslink.local");
    }

    @Test
    @DisplayName("按手机号哈希查：phone_enc 解密回值对象，email 侧因 NULL 回落到合成占位（不可登录）")
    void findByPhoneHashRestoresPhoneOnlyAccount() {
        UserDO d = row(USER_ID, null);
        d.setPhoneEnc("enc-phone");
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(d);
        when(codec.decrypt("enc-phone")).thenReturn("13800138000");

        Account account = repository.findByPhoneHash("H(13800138000)").orElseThrow();

        assertThat(account.getPhone().value()).isEqualTo("13800138000");
        assertThat(account.getEmail().value()).isEqualTo("deactivated-" + USER_ID + "@invalid.local");
    }

    @Test
    @DisplayName("按 id 查：走 selectById（不拼 WHERE），查不到回 Optional.empty")
    void findByIdUsesPrimaryKeyLookup() {
        when(userMapper.selectById(USER_ID)).thenReturn(null);
        assertThat(repository.findById(USER_ID)).isEmpty();

        when(codec.decrypt(anyString())).thenReturn("dev@campuslink.local");
        when(userMapper.selectById(USER_ID)).thenReturn(row(USER_ID, "enc-mail"));
        assertThat(repository.findById(USER_ID)).map(Account::getId).hasValue(USER_ID);
    }

    @Test
    @DisplayName("批读：空 / null 集合不发 SQL（避免 IN () 非法语句），非空逐行转领域并保序")
    void findByIdsShortCircuitsOnEmpty() {
        assertThat(repository.findByIds(List.of())).isEmpty();
        assertThat(repository.findByIds(null)).isEmpty();
        verify(userMapper, never()).selectBatchIds(any());

        when(codec.decrypt(anyString())).thenReturn("dev@campuslink.local");
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(row(1L, "e1"), row(2L, "e2")));

        assertThat(repository.findByIds(List.of(1L, 2L)))
                .extracting(Account::getId).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("状态写入：status 与 delete_at 两列同写，WHERE 只锁 id（注销 / 封禁 / 撤销注销共用）")
    @SuppressWarnings("unchecked")
    void updateStatusWritesStatusAndDeleteAt() {
        repository.updateStatus(USER_ID, AccountStatus.DEACTIVATED, NOW);

        ArgumentCaptor<Wrapper<UserDO>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(userMapper).update(isNull(), captor.capture());
        LambdaUpdateWrapper<UserDO> update = (LambdaUpdateWrapper<UserDO>) captor.getValue();

        assertThat(update.getSqlSet()).contains("status", "delete_at");
        assertThat(update.getParamNameValuePairs().values())
                .contains(AccountStatus.DEACTIVATED.name(), NOW);
        assertThat(update.getSqlSegment()).contains("id");
    }

    @Test
    @DisplayName("匿名化：十三个敏感 / 展示列一并置空并写 anonymized=true（只清可见数据，不动 status）")
    @SuppressWarnings("unchecked")
    void anonymizeClearsAllSensitiveColumns() {
        repository.anonymize(USER_ID, "已注销用户");

        ArgumentCaptor<Wrapper<UserDO>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(userMapper).update(isNull(), captor.capture());
        LambdaUpdateWrapper<UserDO> update = (LambdaUpdateWrapper<UserDO>) captor.getValue();

        assertThat(update.getSqlSet())
                .contains("nickname", "email_enc", "email_hash", "phone_enc", "phone_hash",
                        "student_id_enc", "student_id_hash", "avatar_url",
                        "bio", "major", "school", "grade", "anonymized")
                .doesNotContain("status", "delete_at", "role");
        assertThat(update.getParamNameValuePairs().values()).contains("已注销用户", true);
    }

    @Test
    @DisplayName("冷静期满扫描：DEACTIVATED + 未匿名 + delete_at 非空且 <= now 四个条件齐全")
    void findExpiredDeactivationsAppliesAllFourConditions() {
        when(codec.decrypt(anyString())).thenReturn("dev@campuslink.local");
        when(userMapper.selectList(any(Wrapper.class))).thenReturn(List.of(row(USER_ID, "enc-mail")));

        List<Account> expired = repository.findExpiredDeactivations(NOW);

        assertThat(expired).hasSize(1);
        LambdaQueryWrapper<UserDO> query = capturedSelectList();
        String sql = query.getSqlSegment();
        assertThat(sql).contains("status", "anonymized", "delete_at", "IS NOT NULL", "<=");
        assertThat(query.getParamNameValuePairs().values())
                .contains(AccountStatus.DEACTIVATED.name(), false, NOW);
    }

    // ---------- helpers ----------

    /** 一行最小可用 users 记录：邮箱加密值可空（手机号渠道 / 已匿名化） */
    private static UserDO row(long id, String emailEnc) {
        UserDO d = new UserDO();
        d.setId(id);
        d.setEmailEnc(emailEnc);
        d.setEmailHash(emailEnc == null ? null : "h" + id);
        d.setNickname("张三同学");
        d.setRole(AccountRole.USER.name());
        d.setStatus(AccountStatus.ACTIVE.name());
        d.setVerified(true);
        d.setAnonymized(false);
        return d;
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<UserDO> capturedSelectCount() {
        ArgumentCaptor<Wrapper<UserDO>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(userMapper, org.mockito.Mockito.atLeastOnce()).selectCount(captor.capture());
        return (LambdaQueryWrapper<UserDO>) captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<UserDO> capturedSelectList() {
        ArgumentCaptor<Wrapper<UserDO>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(userMapper, org.mockito.Mockito.atLeastOnce()).selectList(captor.capture());
        return (LambdaQueryWrapper<UserDO>) captor.getValue();
    }
}
