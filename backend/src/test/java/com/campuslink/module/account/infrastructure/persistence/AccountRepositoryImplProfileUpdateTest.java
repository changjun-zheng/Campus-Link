package com.campuslink.module.account.infrastructure.persistence;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.campuslink.module.account.domain.gateway.SensitiveCodec;
import com.campuslink.module.account.domain.model.Account;
import com.campuslink.module.account.domain.model.AccountRole;
import com.campuslink.module.account.domain.model.AccountStatus;
import com.campuslink.module.account.domain.model.EmailAddress;
import com.campuslink.module.account.domain.model.ProfileField;
import com.campuslink.module.account.domain.model.StudentId;
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

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 资料编辑的 SQL 结构测试（F-ACC-007a / CR-071）：应用层 Mockito 看不见"哪些列进了 SET"，
 * 故捕获 wrapper 直接断言渲染出的 SET 与 WHERE（harness 同 {@code PostRepositoryImplUpdateStatusTest}）。
 *
 * <p>本用例的存在理由是**不该出现的那些列**：整行回写会把 {@code role} / {@code status} / {@code email_*}
 * 按"读那一刻"的旧值盖回去，并发窗口内平台侧对该账号的处置（封禁）就被静默抹掉。
 * 这类缺陷在应用层测试里永远看不见，只能在这一层断言。
 */
@ExtendWith(MockitoExtension.class)
class AccountRepositoryImplProfileUpdateTest {

    private static final long USER_ID = 42L;

    @Mock
    private UserMapper userMapper;

    private AccountRepositoryImpl repository;

    @BeforeAll
    static void initTableInfoCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(UserMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, UserDO.class);
    }

    @BeforeEach
    void setUp() {
        repository = new AccountRepositoryImpl(userMapper, mock(SensitiveCodec.class));
    }

    @SuppressWarnings("unchecked")
    private LambdaUpdateWrapper<UserDO> capturedUpdate(Account account, Set<ProfileField> changed) {
        repository.updateProfile(USER_ID, account, changed);
        ArgumentCaptor<Wrapper<UserDO>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(userMapper).update(isNull(), captor.capture());
        return (LambdaUpdateWrapper<UserDO>) captor.getValue();
    }

    @Test
    @DisplayName("改昵称 + 签名：SET 恰为 nickname 与 bio 两列，WHERE 锁定 id")
    void setsOnlyChangedColumns() {
        var changed = EnumSet.of(ProfileField.NICKNAME, ProfileField.BIO);
        LambdaUpdateWrapper<UserDO> update = capturedUpdate(
                account("新昵称", null, "想走 Java", AccountStatus.ACTIVE), changed);

        assertThat(update.getSqlSet()).contains("nickname", "bio").doesNotContain("major");
        assertThat(update.getSqlSegment()).contains("id =");
    }

    @Test
    @DisplayName("反证守门：三列全改时 SET 里**仍不**出现 role / status / email_* / student_id_* / verified / anonymized")
    void neverTouchesPlatformOwnedColumns() {
        LambdaUpdateWrapper<UserDO> update = capturedUpdate(
                account("新昵称", "计算机科学与技术", "想走 Java", AccountStatus.ACTIVE),
                EnumSet.allOf(ProfileField.class));

        assertThat(update.getSqlSet())
                .doesNotContain("role", "status", "email_enc", "email_hash", "phone",
                        "student_id_enc", "student_id_hash", "verified", "anonymized", "delete_at");
    }

    @Test
    @DisplayName("清空 bio（PUT 传空串归一为 null）：SET 里仍有该列、绑定值为 null——漏列等于旧值留在库里")
    void clearingWritesNull() {
        LambdaUpdateWrapper<UserDO> update = capturedUpdate(
                account("张三同学", "软件工程", null, AccountStatus.ACTIVE), Set.of(ProfileField.BIO));

        assertThat(update.getSqlSet()).contains("bio");
        assertThat(update.getParamNameValuePairs()).containsValue((Object) null);
    }

    @Test
    @DisplayName("封禁态账号的定向 UPDATE 也不碰 status 列（状态门在用例层，写入侧再堵一道）")
    void evenForBannedAccountStatusIsNotWritten() {
        LambdaUpdateWrapper<UserDO> update = capturedUpdate(
                account("新昵称", null, null, AccountStatus.BANNED), Set.of(ProfileField.NICKNAME));

        assertThat(update.getSqlSet()).doesNotContain("status");
    }

    @Test
    @DisplayName("幂等（差异集合为空）→ 一条 SQL 都不发：updated_at 因此不推进")
    void emptyChangeSetSendsNoSql() {
        repository.updateProfile(USER_ID, account("张三同学", null, null, AccountStatus.ACTIVE), Set.of());

        verifyNoInteractions(userMapper);
    }

    private static Account account(String nickname, String major, String bio, AccountStatus status) {
        return Account.rehydrate(USER_ID, EmailAddress.of("dev-stu-01@dev.campuslink.local"), null, StudentId.of("888800001"), nickname, null, null, major, null, bio, AccountRole.USER, status, true, false, null, null, null);
    }
}
