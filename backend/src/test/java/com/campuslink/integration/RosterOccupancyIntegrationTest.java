package com.campuslink.integration;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.account.application.AccountApplicationService;
import com.campuslink.module.account.application.CaptchaService;
import com.campuslink.module.account.application.cmd.AccountCommands.RegisterCommand;
import com.campuslink.module.account.domain.gateway.AccountRepository;
import com.campuslink.module.account.domain.gateway.RosterGateway;
import com.campuslink.module.account.domain.gateway.SensitiveCodec;
import com.campuslink.module.account.domain.gateway.VerificationTicketStore;
import com.campuslink.module.account.domain.model.Account;
import com.campuslink.module.account.domain.model.EmailAddress;
import com.campuslink.module.account.domain.model.StudentId;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 名册占用与注册事务的真实数据库集成测试（R-6）。
 *
 * <p>
 * 验证“一学号一账号”的两条防线：① {@code student_roster.used_user_id} 的原子占用与账号插入在同一事务内，
 * 任一步失败整体回滚；② 并发注册同一学号时只落一个账号。仅验证码与票据之外的部分走真实 MySQL / Redis，
 * 验证码用替身避免依赖发码通道（不是本测试的验证对象）。
 */
@SpringBootTest(properties = "campuslink.roster.bypass=false")
@EnabledIfEnvironmentVariable(named = "SPRING_IT", matches = "true")
class RosterOccupancyIntegrationTest {

    private static final Duration TICKET_TTL = Duration.ofMinutes(5);
    private static final String CAPTCHA = "123456";
    private static final long ORPHAN_OWNER = 987_654_321L;

    @Autowired
    private AccountApplicationService accounts;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private RosterGateway roster;
    @Autowired
    private SensitiveCodec codec;
    @Autowired
    private VerificationTicketStore tickets;
    @Autowired
    private PlatformTransactionManager transactions;
    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private CaptchaService captcha;

    private final List<String> cleanupHashes = new ArrayList<>();

    @BeforeEach
    void captchaAlwaysPasses() {
        when(captcha.verify(anyString(), anyString())).thenReturn(true);
    }

    @AfterEach
    void cleanUp() {
        for (String hash : cleanupHashes) {
            jdbc.update("DELETE FROM users WHERE student_id_hash = ?", hash);
            jdbc.update("DELETE FROM student_roster WHERE student_id_hash = ?", hash);
        }
        cleanupHashes.clear();
    }

    @Test
    void accountInsertAndRosterOccupyRollBackTogether() {
        String studentId = newStudentId();
        String hash = insertRosterRow(studentId, null);

        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            Account account = accountRepository.save(Account.registered(
                    EmailAddress.of(uniqueEmail()), null, StudentId.of(studentId), "事务回滚用户"));
            assertThat(roster.occupy(hash, account.getId())).isTrue();
            assertThat(occupiedBy(hash)).isEqualTo(account.getId());
            tx.setRollbackOnly();
        });

        assertThat(countUsers(hash)).isZero();
        assertThat(occupiedBy(hash)).isNull();
    }

    @Test
    void occupyConflictRejectsRegistrationAndRollsBackInsertedAccount() {
        String studentId = newStudentId();
        String hash = insertRosterRow(studentId, ORPHAN_OWNER);
        String ticket = tickets.issue(codec.encrypt(studentId), TICKET_TTL);

        assertThatThrownBy(() -> accounts.register(registerCommand(ticket, uniqueEmail())))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ResultCode.STUDENT_VERIFY_FAILED));
        assertThat(countUsers(hash)).isZero();
        assertThat(occupiedBy(hash)).isEqualTo(ORPHAN_OWNER);
    }

    @Test
    void concurrentRegistrationForSameStudentIdLeavesExactlyOneAccount() throws Exception {
        String studentId = newStudentId();
        String hash = insertRosterRow(studentId, null);
        CountDownLatch start = new CountDownLatch(1);
        List<Boolean> outcomes = new CopyOnWriteArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Void> attempt = () -> {
                String ticket = tickets.issue(codec.encrypt(studentId), TICKET_TTL);
                start.await(10, TimeUnit.SECONDS);
                try {
                    accounts.register(registerCommand(ticket, uniqueEmail()));
                    outcomes.add(true);
                } catch (ApiException ex) {
                    assertThat(ex.getCode()).isEqualTo(ResultCode.STUDENT_VERIFY_FAILED);
                    outcomes.add(false);
                }
                return null;
            };
            List<Future<Void>> futures = List.of(pool.submit(attempt), pool.submit(attempt));
            start.countDown();
            for (Future<Void> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(outcomes).containsExactlyInAnyOrder(true, false);
        assertThat(countUsers(hash)).isOne();
        assertThat(occupiedBy(hash)).isEqualTo(ownerOf(hash));
    }

    private String insertRosterRow(String studentId, Long usedUserId) {
        String hash = codec.hash(studentId);
        cleanupHashes.add(hash);
        jdbc.update("INSERT INTO student_roster(student_id_hash, name, grade, source_batch, used_user_id) "
                + "VALUES (?, ?, ?, ?, ?)", hash, "测试学生", "2024", "r6-integration", usedUserId);
        return hash;
    }

    private static RegisterCommand registerCommand(String ticket, String email) {
        return new RegisterCommand(ticket, email, CAPTCHA, "集成测试用户");
    }

    private static String newStudentId() {
        return "99" + String.format("%07d", ThreadLocalRandom.current().nextInt(10_000_000));
    }

    private static String uniqueEmail() {
        return "it-" + UUID.randomUUID().toString().replace("-", "") + "@it.campuslink.local";
    }

    private long countUsers(String hash) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE student_id_hash = ?", Long.class, hash);
        return count == null ? 0 : count;
    }

    private Long ownerOf(String hash) {
        return jdbc.queryForObject("SELECT id FROM users WHERE student_id_hash = ?", Long.class, hash);
    }

    private Long occupiedBy(String hash) {
        return jdbc.queryForObject("SELECT used_user_id FROM student_roster WHERE student_id_hash = ?",
                Long.class, hash);
    }
}
