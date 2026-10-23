package com.campuslink.module.account.application;

import com.campuslink.common.audit.AuditService;
import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.account.application.cmd.AccountCommands.UpdateProfileCommand;
import com.campuslink.module.account.domain.gateway.AccountRepository;
import com.campuslink.module.account.domain.gateway.RateLimitGateway;
import com.campuslink.module.account.domain.model.Account;
import com.campuslink.module.account.domain.model.AccountStatus;
import com.campuslink.module.account.domain.model.ProfileField;
import com.campuslink.module.forum.application.ModerationApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 资料编辑用例（F-ACC-007a + 007e，应用层）：限流 → 状态门 → 聚合改规则 → 定向 UPDATE → 审计。
 *
 * <p>
 * 单开一个服务而不是塞进 {@link AccountApplicationService}：后者是"账号生命周期"（核验 / 注册 / 登录），
 * 依赖 10 个端口；本用例只依赖仓储、限流与审计三件，分开后两条链路各自的构造都不再膨胀。
 *
 * <p>
 * 昵称与签名自 CR-122 起进入机审调用面（与发帖 / 回帖同一网关，本地词表模式下命中 `5001 / 422`）；
 * 限流与审计仍是本用例的另外两道缓解。
 */
@Service
@RequiredArgsConstructor
public class ProfileApplicationService {

    /** 每小时提交上限：口径对齐基线 §4 学籍核验的"10 次/小时"档位（PRD §4.3 007e），故不新开配置项 */
    private static final int HOURLY_LIMIT = 10;
    private static final Duration WINDOW = Duration.ofHours(1);

    private final AccountRepository accountRepository;
    private final RateLimitGateway rateLimitGateway;
    private final AuditService auditService;
    private final ModerationApplicationService moderationService;

    /**
     * 修改本人资料（昵称 / 专业 / 签名）。返回改动后的聚合，web 层据此回显全量 {@code UserVo}（前端不必二次拉取）。
     *
     * <p>
     * 限流判据是 {@code hits > HOURLY_LIMIT} 而非 {@code >=}：{@code hitAndCount} **先增后判**，
     * 用 {@code >=} 会在第 10 次就拒掉、把配额写成 9 次。放在最前面是 PRD 的硬断言——
     * 第 11 次请求不得触达 UPDATE（读库与审计同样都不该发生）。
     *
     * <p>
     * 状态门只判 {@code BANNED}：{@code DEACTIVATED}（注销冷静期）按 PRD 允许编辑，但本期它
     * **无路径可达**——登录判定用 {@code isActive()}，冷静期账号拿不到令牌；该冲突登记待发起人裁决，
     * 不在此处静默改登录口径。
     */
    @Transactional
    public Account updateProfile(long userId, UpdateProfileCommand command) {
        long hits = rateLimitGateway.hitAndCount("profile:edit:" + userId, WINDOW);
        if (hits > HOURLY_LIMIT) {
            throw new ApiException(ResultCode.PROFILE_UPDATE_TOO_FREQUENT);
        }
        // CR-122 / CR-123：限流之后、读库之前拦下违规昵称 / 专业 / 签名（null = 本字段不改，由网关入口跳过）
        moderationService.ensureAllowed(command.nickname(), command.major(), command.bio());
        Account account = accountRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ResultCode.USER_NOT_FOUND));
        if (account.getStatus() == AccountStatus.BANNED) {
            throw new ApiException(ResultCode.USER_BANNED);
        }

        Set<ProfileField> changed = account.updateProfile(command.nickname(), command.major(), command.bio());
        if (changed.isEmpty()) {
            // 幂等：三值全等 → 不发 SQL、不推进 updated_at、不记审计（配额已消耗，空提交绕不过限流）
            return account;
        }
        accountRepository.updateProfile(userId, account, changed);
        auditService.record(userId, "PROFILE_UPDATE", "users", userId, auditDetail(changed));
        return account;
    }

    /** detail 只记**变更字段名**：新值已在 users 行内，落两份就是敏感信息放大（运营要旧值请查变更历史，本期无） */
    private static String auditDetail(Set<ProfileField> changed) {
        return "fields=" + changed.stream().map(ProfileField::auditName).collect(Collectors.joining(","));
    }
}
