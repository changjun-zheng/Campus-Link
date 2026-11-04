package com.campuslink.module.account.infrastructure.seed;

import com.campuslink.module.account.domain.gateway.AccountRepository;
import com.campuslink.module.account.domain.gateway.SensitiveCodec;
import com.campuslink.module.account.domain.model.Account;
import com.campuslink.module.account.domain.model.AccountRole;
import com.campuslink.module.account.domain.model.EmailAddress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 开发期种子：预置运营（OPS）账号（CR-196）。
 *
 * <p>与 {@link DevAdminAccountSeeder} 同一门控（{@code campuslink.roster.bypass=true}）——
 * bypass 为 false 时不装配，即生产环境不存在该账号。运营不是学生，无学号、不走学籍核验，
 * 直接以 OPS 建号，用于联调举报工单 / 内容巡查 / 内容检索与角色边界验收。
 *
 * <p>幂等：按邮箱哈希判存，重启不会重复插入。登录方式与普通用户一致（邮箱 + 验证码）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "campuslink.roster.bypass", havingValue = "true", matchIfMissing = true)
public class DevOpsAccountSeeder implements ApplicationRunner {

    private static final String OPS_EMAIL = "ops@campuslink.local";
    private static final String OPS_NICKNAME = "运营";

    private final AccountRepository accountRepository;
    private final SensitiveCodec codec;

    @Override
    public void run(ApplicationArguments args) {
        String emailHash = codec.hash(EmailAddress.of(OPS_EMAIL).value());
        if (accountRepository.existsByEmailHash(emailHash)) {
            log.info("[DEV] 运营账号已存在，跳过种子：{}", OPS_EMAIL);
            return;
        }
        Account ops = Account.provisioned(EmailAddress.of(OPS_EMAIL), OPS_NICKNAME, AccountRole.OPS);
        accountRepository.save(ops);
        log.warn("[DEV] 已预置运营账号 {}（OPS，无学号）——仅开发期存在，bypass=false 时不会创建", OPS_EMAIL);
    }
}
