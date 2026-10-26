package com.campuslink.module.account.application;

import com.campuslink.common.audit.AuditService;
import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.config.AppProperties;
import com.campuslink.module.account.application.cmd.AccountCommands.LoginCommand;
import com.campuslink.module.account.application.cmd.AccountCommands.LoginResult;
import com.campuslink.module.account.application.cmd.AccountCommands.RegisterCommand;
import com.campuslink.module.account.application.cmd.AccountCommands.VerifyStudentCommand;
import com.campuslink.module.account.application.cmd.AccountCommands.VerifyStudentResult;
import com.campuslink.module.account.domain.event.AccountRegisteredEvent;
import com.campuslink.module.account.domain.exception.AccountConflictException;
import com.campuslink.module.account.domain.gateway.AccountRepository;
import com.campuslink.module.account.domain.gateway.RateLimitGateway;
import com.campuslink.module.account.domain.gateway.RosterGateway;
import com.campuslink.module.account.domain.gateway.SensitiveCodec;
import com.campuslink.module.account.domain.gateway.TokenIssuer;
import com.campuslink.module.account.domain.gateway.VerificationTicketStore;
import com.campuslink.module.account.domain.model.Account;
import com.campuslink.module.account.domain.model.AccountRole;
import com.campuslink.module.account.domain.model.AccountStatus;
import com.campuslink.module.account.domain.model.CohortYear;
import com.campuslink.module.account.domain.model.Contact;
import com.campuslink.module.account.domain.model.StudentId;
import com.campuslink.module.account.domain.service.StudentVerificationService;
import com.campuslink.module.forum.application.ModerationApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 账号用例编排（应用层）：核验 → 注册 → 登录。
 * 只依赖 domain 端口与领域服务，不接触 MyBatis-Plus / Redis 细节（端口-适配器，DIP）。
 * 对外 API 契约与重构前完全一致（路径 / 响应结构不变）。
 */
@Service
@RequiredArgsConstructor
public class AccountApplicationService {

    private final StudentVerificationService verificationService;
    private final AccountRepository accountRepository;
    private final RosterGateway rosterGateway;
    private final VerificationTicketStore ticketStore;
    private final CaptchaService captchaService;
    private final SensitiveCodec codec;
    private final TokenIssuer tokenIssuer;
    private final RateLimitGateway rateLimitGateway;
    private final AuditService auditService;
    private final AppProperties props;
    private final ApplicationEventPublisher eventPublisher;
    private final ModerationApplicationService moderationService;

    /** 学籍核验（F-ACC-004）：IP 限流 → 比对 → 签发一次性票据（payload 为加密学号） */
    public VerifyStudentResult verifyStudent(VerifyStudentCommand command, String ip) {
        long hits = rateLimitGateway.hitAndCount("verify:ip:" + ip, Duration.ofHours(1));
        if (hits > props.getVerify().getIpHourlyLimit()) {
            throw new ApiException(ResultCode.VERIFY_RATE_LIMITED);
        }
        StudentId studentId = StudentId.of(command.studentId());
        if (!verificationService.matches(studentId, command.name())) {
            // 统一失败提示：学号不存在 / 姓名不匹配 / 已注册不区分（防名册枚举）
            throw new ApiException(ResultCode.STUDENT_VERIFY_FAILED);
        }
        String ticket = ticketStore.issue(codec.encrypt(studentId.value()),
                Duration.ofMinutes(props.getVerify().getTicketTtlMinutes()));
        return new VerifyStudentResult(ticket);
    }

    /**
     * 注册（F-ACC-001 + F-ACC-004，CR-093 起支持手机号渠道）：验证码 → 消费票据 → 建号 → 占用名册学号（事务） →
     * 发布领域事件。
     * {@code contact} 为邮箱**或**手机号（二合一，格式门在 web 侧 @Pattern）：验证码存取键与冲突预检都走规范化后的
     * contact。
     */
    @Transactional
    public LoginResult register(RegisterCommand command) {
        // CR-122：昵称进入机审调用面（与发帖 / 回帖同一网关与 fail-closed 口径），放在任何副作用之前
        moderationService.ensureAllowed(command.nickname());
        Contact contact = Contact.parse(command.contact());
        if (!captchaService.verify(contact.normalized(), command.code())) {
            throw new ApiException(ResultCode.CAPTCHA_INVALID);
        }
        // 票据载荷是加密后的学号（VerificationTicketStore 约定：不含明文学号），此处必须解密还原
        String studentId = ticketStore.consume(command.ticket())
                .map(codec::decrypt)
                .orElseThrow(() -> new ApiException(ResultCode.VERIFY_TICKET_INVALID));
        if (contact.hasEmail() && accountRepository.existsByEmailHash(codec.hash(contact.email().value()))) {
            throw new ApiException(ResultCode.EMAIL_EXISTS);
        }
        if (contact.hasPhone() && accountRepository.existsByPhoneHash(codec.hash(contact.phone().value()))) {
            throw new ApiException(ResultCode.PHONE_EXISTS);
        }

        Account account;
        try {
            // 入学年级快照（CR-145 届徽章）：建号前读一次名册行（此刻尚未占用，行必为 available），
            // 归一后随账号落库。查不到（并发占用 / bypass）不快照为 null，不影响注册成败——
            // 年级是展示资料，不是注册前置；随后 occupy 失败仍会整体回滚
            String cohortYear = rosterGateway.findAvailableByHash(codec.hash(studentId))
                    .map(record -> CohortYear.normalize(record.grade()))
                    .orElse(null);
            account = accountRepository.save(Account.registered(
                    contact.email(), contact.phone(), StudentId.of(studentId), command.nickname(), cohortYear));
        } catch (AccountConflictException e) {
            // 邮箱 / 手机号冲突可明确提示（二者均非名册信息）；学号冲突必须与其它核验失败**同提示**，
            // 否则可据提示差异枚举出"哪些学号在名册中且已被占用"（PRD F-ACC-004 防名册枚举）
            throw new ApiException(switch (e.field()) {
                case EMAIL -> ResultCode.EMAIL_EXISTS;
                case PHONE -> ResultCode.PHONE_EXISTS;
                case STUDENT_ID -> ResultCode.STUDENT_VERIFY_FAILED;
            });
        }
        if (!rosterGateway.occupy(codec.hash(studentId), account.getId())) {
            // 并发注册占用冲突：抛出使整个事务回滚（含刚插入的账号）
            throw new ApiException(ResultCode.STUDENT_VERIFY_FAILED);
        }

        captchaService.consume(contact.normalized());
        eventPublisher.publishEvent(new AccountRegisteredEvent(account.getId(), account.getNickname(), Instant.now()));
        return toResult(account);
    }

    /**
     * 登录（CR-093 / F-011）：{@code account} 为邮箱**或**手机号，据形态分流查找通道；
     * 统一提示不区分“账号不存在”与“验证码错误”；封禁账号拒绝。
     */
    public LoginResult login(LoginCommand command) {
        Contact contact;
        try {
            contact = Contact.parse(command.account());
        } catch (IllegalArgumentException e) {
            // @Pattern 已在 web 侧拦格式，此处兜底：格式非法按登录失败统一提示（与防枚举同口径）
            throw new ApiException(ResultCode.LOGIN_FAILED);
        }
        Account account = (contact.hasPhone()
                ? accountRepository.findByPhoneHash(codec.hash(contact.phone().value()))
                : accountRepository.findByEmailHash(codec.hash(contact.email().value()))).orElse(null);
        if (account == null || !captchaService.verify(contact.normalized(), command.code())) {
            throw new ApiException(ResultCode.LOGIN_FAILED);
        }
        if (account.getStatus() == AccountStatus.DEACTIVATED) {
            // 注销冷静期（F-ACC-003 / CR-082）：期内登录即视为撤销注销；期满拒绝（2011）
            if (account.getDeleteAt() != null && account.getDeleteAt().isAfter(Instant.now())) {
                accountRepository.updateStatus(account.getId(), AccountStatus.ACTIVE, null);
                auditService.record(account.getId(), "ACCOUNT_REACTIVATE", "users", account.getId(), null);
                captchaService.consume(contact.normalized());
                // 回读刷新状态：撤销后响应里的 status 必须是 ACTIVE，而不是内存里的 DEACTIVATED
                Account reactivated = accountRepository.findById(account.getId()).orElse(account);
                return toResult(reactivated);
            }
            throw new ApiException(ResultCode.ACCOUNT_EXPIRED);
        }
        if (!account.isActive()) {
            throw new ApiException(ResultCode.USER_BANNED);
        }
        captchaService.consume(contact.normalized());
        return toResult(account);
    }

    private LoginResult toResult(Account account) {
        String token = tokenIssuer.issue(account.getId(), account.getRole().name());
        long expiresAt = Instant.now().plusSeconds(tokenIssuer.ttlSeconds()).getEpochSecond();
        return new LoginResult(token, expiresAt, account);
    }

    /**
     * 登录态续签（F-ACC-001 / CR-116）：凭当前有效 JWT 换新 token，实现"7 天滑动续期"。
     *
     * <p>
     * <b>为什么重读账号而不是直接按 claims 重签</b>：封禁 / 注销中的账号不能靠"续签"把会话
     * 无限延长；角色也从库里取，让角色调整在续签时生效。{@code DEACTIVATED} 不续签——续签等于
     * 绕过"注销冷静期内再次登录即撤销注销"的语义，用户须重新登录走一次撤销链路。
     */
    public LoginResult refresh(long userId) {
        Account account = accountRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ResultCode.USER_NOT_FOUND));
        if (account.getStatus() == AccountStatus.DEACTIVATED) {
            throw new ApiException(ResultCode.ACCOUNT_DEACTIVATED);
        }
        if (!account.isActive()) {
            throw new ApiException(ResultCode.USER_BANNED);
        }
        return toResult(account);
    }

    /**
     * 批量取昵称：其他上下文（forum）展示作者名的唯一入口（ADR-012 跨上下文只调对方 application）。
     * 一次查询取齐整页作者，不做 N+1；查不到的 id 不出现在结果中，由调用方决定回落文案。
     */
    public Map<Long, String> nicknamesOf(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return accountRepository.findByIds(ids).stream()
                .collect(Collectors.toMap(Account::getId, Account::getNickname));
    }

    /**
     * 按昵称关键字解析账号 id（F-ADMIN-002 / CR-141）：forum 后台内容搜索的"按用户"筛选唯一入口。
     *
     * <p>跨上下文方向合法（ADR-012：只调对方 application）；上限 50 条——后台搜索只需缩小作者集合，
     * 不承担"列出全部同名账号"的职责，超出部分由下一次更精确的关键词收敛。
     */
    public List<Long> accountIdsByNickname(String keyword) {
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty()) {
            return List.of();
        }
        return accountRepository.findIdsByNicknameLike(kw, 50);
    }

    /**
     * 按 id 取账号（只读）：web 层展示当前用户资料的入口。
     * 存在意义是 R-1 裁决（CR-028）：web 不得注入 domain 端口，只允许经 application。
     */
    public Account accountOf(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException(ResultCode.USER_NOT_FOUND));
    }

    /** 注销（F-ACC-003 / CR-082）：置 DEACTIVATED + 7 天冷静期；期内登录即撤销，期满由匿名化定时任务处理 */
    @Transactional
    public void deactivate(long userId) {
        accountOf(userId);
        accountRepository.updateStatus(userId, AccountStatus.DEACTIVATED, Instant.now().plus(Duration.ofDays(7)));
        auditService.record(userId, "ACCOUNT_DEACTIVATE", "users", userId, "coolingDays=7");
    }

    /** 内容发布前置（CR-082）：封禁（2006）与注销中（2013）均不得发帖 / 回帖 */
    public void assertActiveForContent(long userId) {
        Account a = accountOf(userId);
        if (a.getStatus() == AccountStatus.BANNED) {
            throw new ApiException(ResultCode.USER_BANNED);
        }
        if (a.getStatus() == AccountStatus.DEACTIVATED) {
            throw new ApiException(ResultCode.ACCOUNT_DEACTIVATED);
        }
    }

    /**
     * 管理员封禁 / 解封（CR-082 治理）：超级管理员不可被封禁；全程审计。forum 侧工单 REMOVE 用户时经此方法（跨上下文不带 domain
     * 类型）
     */
    public void adminSetStatus(long operatorId, long targetId, AccountStatus target) {
        Account a = accountOf(targetId);
        if (a.getRole() == AccountRole.SUPERADMIN) {
            throw new ApiException(ResultCode.FORBIDDEN);
        }
        accountRepository.updateStatus(targetId, target, null);
        auditService.record(operatorId, target == AccountStatus.BANNED ? "USER_BAN" : "USER_UNBAN",
                "users", targetId, null);
    }

    /** 封禁（工单 REMOVE 用户目标的专用入口，避免 forum 引用 AccountStatus domain 类型） */
    public void adminBan(long operatorId, long targetId) {
        adminSetStatus(operatorId, targetId, AccountStatus.BANNED);
    }

    /**
     * 按 id 取**可公开露出**的账号（他人主页 {@code /u/:id} 与它的公开帖子列表共用这一道门）：
     * 不存在与已注销（{@code DEACTIVATED}）一律 2007 / 404，两种情形对外不区分——与帖子侧
     * {@code requireVisiblePost} 把「不存在 / 已删 / 未发布」全归 3001 是同一条纪律：不给按 id 探测账号状态的口子。
     *
     * <p>
     * 与 {@link #accountOf} 的差别只多一道注销门，不合并成一个方法是因为两者的调用方语义不同：
     * {@code accountOf} 答「这个 id 有没有对应账号」（关注 / 令牌校验用，注销中的账号仍是关注关系的一方），
     * 本方法答「这个账号能不能被路人看见」。
     *
     * <p>
     * {@code BANNED} **不在这里拦**：封禁限的是发言与登录，不是把已发布内容从站内抹掉
     * （全站列表 / 搜索 / 热榜从不按作者状态过滤）；主页若单独消失，列表里那个作者名就点不开了。
     */
    public Account publicAccountOf(long accountId) {
        Account account = accountOf(accountId);
        if (account.getStatus() == AccountStatus.DEACTIVATED) {
            throw new ApiException(ResultCode.USER_NOT_FOUND);
        }
        return account;
    }

    /**
     * 运营看板（F-ADMIN-005 / CR-131）：按状态分组的账号计数，供 forum 侧数据看板消费。
     *
     * <p>跨上下文方向合法（ADR-012：只调对方 application）；只读、不做任何逐行扫描。
     * {@code total} 含已注销账号（它们仍是账号记录），由看板单列 {@code deactivated}。
     */
    public AccountStats accountStats() {
        Map<AccountStatus, Long> byStatus = accountRepository.countByStatus();
        return new AccountStats(byStatus.values().stream().mapToLong(Long::longValue).sum(),
                byStatus.getOrDefault(AccountStatus.ACTIVE, 0L),
                byStatus.getOrDefault(AccountStatus.BANNED, 0L),
                byStatus.getOrDefault(AccountStatus.DEACTIVATED, 0L));
    }

    /** 运营看板用户组出参（application 层载体，web 层再映射成 VO） */
    public record AccountStats(long total, long active, long banned, long deactivated) {
    }
}
