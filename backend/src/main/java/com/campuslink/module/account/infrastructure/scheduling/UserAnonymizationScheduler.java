package com.campuslink.module.account.infrastructure.scheduling;

import com.campuslink.common.audit.AuditService;
import com.campuslink.module.account.domain.gateway.AccountRepository;
import com.campuslink.module.account.domain.model.Account;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * 注销匿名化定时任务（F-ACC-003 / CR-082）：每小时扫一次「冷静期已满且未撤销」的注销账号，
 * 清除实名信息与展示资料（email / phone / 学号 / 头像 / bio / major / school / grade）、
 * 昵称置占位、anonymized = true，并逐条审计（CR-127 补齐展示资料四列——只清实名信息会让
 * 匿名化账号的主页继续展示原 bio / major）。
 *
 * <p>幂等：任务只挑 anonymized = 0 的行，重复执行无二次写。MVP 单实例部署，未加分布式锁
 * （热榜任务的 Redis 锁模式可复用，多实例时再引入——如实登记，实施方案 §2）。
 *
 * <p>装配开关 {@code campuslink.account.anonymization.enabled}（CR-090）：缺省即启用
 * （matchIfMissing = true，既有行为零变化），供 {@code SpringBootContextTest} 关闭调度副作用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "campuslink.account.anonymization.enabled", havingValue = "true", matchIfMissing = true)
public class UserAnonymizationScheduler {

    private final AccountRepository accountRepository;
    private final AuditService auditService;

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
    public void anonymizeExpired() {
        List<Account> due = accountRepository.findExpiredDeactivations(Instant.now());
        for (Account account : due) {
            accountRepository.anonymize(account.getId(), "已注销用户");
            auditService.record(account.getId(), "ACCOUNT_ANONYMIZE", "users", account.getId(), null);
            log.info("注销匿名化完成：accountId={}", account.getId());
        }
        if (!due.isEmpty()) {
            log.info("注销匿名化本轮处理 {} 个账号", due.size());
        }
    }
}
