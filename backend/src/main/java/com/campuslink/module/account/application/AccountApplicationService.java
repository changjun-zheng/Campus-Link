package com.campuslink.module.account.application;

import com.campuslink.common.audit.AuditService;
import com.campuslink.common.event.UserMutedEvent;
import com.campuslink.common.event.UserWarnedEvent;
import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.common.security.TokenRevocationGateway;
import com.campuslink.config.AppProperties;
import com.campuslink.module.account.application.cmd.AccountCommands.LoginCommand;
import com.campuslink.module.account.application.cmd.AccountCommands.LoginResult;
import com.campuslink.module.account.application.cmd.AccountCommands.RegisterCommand;
import com.campuslink.module.account.application.cmd.AccountCommands.VerifyStudentCommand;
import com.campuslink.module.account.application.cmd.AccountCommands.VerifyStudentResult;
import com.campuslink.module.account.application.cmd.AdminUserSearchResult;
import com.campuslink.module.account.domain.event.AccountRegisteredEvent;
import com.campuslink.module.account.domain.model.DefaultAvatar;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
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
    private final TokenRevocationGateway tokenRevocationGateway;

    /** 后台用户检索最多返回条数（CR-211）；多取 1 条用于判断是否被截断 */
    private static final int ADMIN_USER_SEARCH_LIMIT = 50;

    /** 后台用户检索关键字长度上限：昵称最长 32、用户 id 最长 19，留出余量后统一拦 50 */
    private static final int ADMIN_USER_SEARCH_KEYWORD_MAX = 50;

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
            // 默认头像（CR-162）：自增 id 落库后才可映射；条件 UPDATE 只写空列，并把值回填到聚合，
            // 让本次注册响应（UserVo.avatarUrl）立即带图。id 为空只可能出现在违约的适配器 / 测试替身，
            // 此时跳过头像而不是让注册失败（头像属展示资料，不是注册前置）
            Long accountId = account.getId();
            if (accountId != null) {
                String defaultAvatar = DefaultAvatar.forUser(accountId);
                accountRepository.updateAvatarIfAbsent(accountId, defaultAvatar);
                account.assignAvatarIfAbsent(defaultAvatar);
            }
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
     * 退出登录（CR-213）：把该用户的吊销水位推到当前时刻，所有旧 token 下一请求即失效。
     *
     * <p>项目没有逐设备会话模型，退出 = 全端退出；不记审计（非业务状态变更，避免每次退出刷一条）。
     */
    public void logout(long userId) {
        tokenRevocationGateway.revokeAllBefore(userId, Instant.now());
    }

    /**
     * 批量取作者摘要（昵称 + 头像）：其他上下文（forum / notification）展示作者的唯一入口
     * （ADR-012 跨上下文只调对方 application）。一次查询取齐整页作者，不做 N+1；
     * 查不到的 id 不出现在结果中，由调用方决定回落文案。
     *
     * <p>CR-162 起由 {@code nicknamesOf} 升级而来：forum 列表 / 详情 / 楼层需要头像，
     * 若另开一个只取头像的方法会变成两次批量查询，故一次返回 {@link AuthorBrief}。
     */
    public Map<Long, AuthorBrief> authorBriefsOf(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return accountRepository.findByIds(ids).stream()
                .collect(Collectors.toMap(Account::getId,
                        account -> new AuthorBrief(account.getNickname(), account.getAvatarUrl())));
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
     * 后台用户检索（CR-211，仅 SUPERADMIN）：纯数字关键字先按 id 精确命中，再按昵称 LIKE；
     * 结果按「精确 id 优先、其余 id 倒序」返回，最多 50 条。
     *
     * <p>只查昵称与 id：邮箱 / 手机号 / 学号是敏感字段，等值查询会扩大后台的枚举面，
     * 本 CR 明确不做。查不到不是错误，返回空列表即可；空关键字才是调用方参数错误。
     */
    public AdminUserSearchResult adminSearchUsers(String keyword) {
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty() || kw.length() > ADMIN_USER_SEARCH_KEYWORD_MAX) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }

        Map<Long, Account> matched = new LinkedHashMap<>();
        if (kw.chars().allMatch(Character::isDigit) && kw.length() <= 19) {
            try {
                accountRepository.findById(Long.parseLong(kw))
                        .ifPresent(account -> matched.put(account.getId(), account));
            } catch (NumberFormatException ignored) {
                // 19 位以内仍可能溢出 long：按昵称关键字继续查，不把输入判成参数错误
            }
        }

        List<Long> nicknameIds = accountRepository.findIdsByNicknameLike(
                kw, ADMIN_USER_SEARCH_LIMIT + 1);
        boolean truncated = nicknameIds.size() > ADMIN_USER_SEARCH_LIMIT;
        List<Long> pageIds = truncated
                ? nicknameIds.subList(0, ADMIN_USER_SEARCH_LIMIT)
                : nicknameIds;
        accountRepository.findByIds(pageIds).stream()
                .sorted(Comparator.comparing(Account::getId).reversed())
                .forEach(account -> matched.putIfAbsent(account.getId(), account));

        // 精确 id 命中会叠加在 50 条昵称命中之上：合并后再兜一次上限，避免返回 51 条
        boolean mergedOverflow = matched.size() > ADMIN_USER_SEARCH_LIMIT;
        List<AdminUserSearchResult.Item> items = matched.values().stream()
                .limit(ADMIN_USER_SEARCH_LIMIT)
                .map(account -> new AdminUserSearchResult.Item(account.getId(), account.getNickname(),
                        account.getRole(), account.getStatus(), account.getMutedUntil(),
                        account.getCreatedAt()))
                .toList();
        return new AdminUserSearchResult(items, truncated || mergedOverflow);
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
        // 旧 token 立即失效：冷静期内只能重新登录（登录即撤销注销），不能沿用注销前的会话
        tokenRevocationGateway.revokeAllBefore(userId, Instant.now());
        auditService.record(userId, "ACCOUNT_DEACTIVATE", "users", userId, "coolingDays=7");
    }

    /** 账号状态前置（CR-082）：封禁（2006）与注销中（2013）拒绝；读路径也用它，故**不含禁言** */
    public void assertActiveForContent(long userId) {
        requireContentAccount(userId);
    }

    /**
     * 内容写入前置（CR-209）：在 {@link #assertActiveForContent} 之上追加禁言判定（2019）。
     * 写路径用它、读路径（如草稿图片读取）继续用前者——禁言只限制发布，不冻结已有内容的查看与编辑入口。
     */
    public void assertAllowedToWriteContent(long userId) {
        if (requireContentAccount(userId).isMutedAt(Instant.now())) {
            throw new ApiException(ResultCode.USER_MUTED);
        }
    }

    private Account requireContentAccount(long userId) {
        Account a = accountOf(userId);
        if (a.getStatus() == AccountStatus.BANNED) {
            throw new ApiException(ResultCode.USER_BANNED);
        }
        if (a.getStatus() == AccountStatus.DEACTIVATED) {
            throw new ApiException(ResultCode.ACCOUNT_DEACTIVATED);
        }
        return a;
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
        if (target == AccountStatus.BANNED) {
            // 封禁即时登出：旧 token 下一请求即按未登录处理，不等 7 天自然过期
            tokenRevocationGateway.revokeAllBefore(targetId, Instant.now());
        }
        auditService.record(operatorId, target == AccountStatus.BANNED ? "USER_BAN" : "USER_UNBAN",
                "users", targetId, null);
    }

    /** 封禁（工单 REMOVE 用户目标的专用入口，避免 forum 引用 AccountStatus domain 类型） */
    public void adminBan(long operatorId, long targetId) {
        adminSetStatus(operatorId, targetId, AccountStatus.BANNED);
    }

    /**
     * 授予运营角色（F-ADMIN-001 / CR-196）：只允许 **ACTIVE + USER → OPS**，幂等；超管与注销 / 封禁账号拒绝。
     *
     * <p>升权后立即吊销目标用户旧 token（CR-208）：目标须重新登录才会拿到带 OPS
     * 的新 token，旧 token 不会以 USER 身份继续使用；降级见 {@link #adminDemoteToUser}（CR-212）。
     */
    @Transactional
    public void adminPromoteToOps(long operatorId, long targetId) {
        Account a = accountOf(targetId);
        if (a.getRole() == AccountRole.OPS) {
            return;
        }
        if (a.getRole() != AccountRole.USER || a.getStatus() != AccountStatus.ACTIVE) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
        if (accountRepository.updateRole(targetId, AccountRole.USER, AccountRole.OPS)) {
            // 升权须重新登录才能拿到带 OPS 的新 token；旧 token 的 USER 角色不再可用
            tokenRevocationGateway.revokeAllBefore(targetId, Instant.now());
            auditService.record(operatorId, "USER_ROLE_UPDATE", "users", targetId, "role=OPS");
        }
    }

    /**
     * 撤销运营角色（CR-212）：只允许 **OPS → USER**，幂等；SUPERADMIN 不可经本入口降级。
     *
     * <p>撤权后立即吊销目标用户旧 token（复用 CR-208 水位）：旧 token 下一请求即失效，
     * 重新登录后为普通用户；条件更新带当前角色，避免与并发升权互相覆盖。
     */
    @Transactional
    public void adminDemoteToUser(long operatorId, long targetId) {
        Account a = accountOf(targetId);
        if (a.getRole() == AccountRole.USER) {
            return;
        }
        if (a.getRole() != AccountRole.OPS) {
            throw new ApiException(ResultCode.FORBIDDEN);
        }
        if (accountRepository.updateRole(targetId, AccountRole.OPS, AccountRole.USER)) {
            tokenRevocationGateway.revokeAllBefore(targetId, Instant.now());
            auditService.record(operatorId, "USER_ROLE_UPDATE", "users", targetId, "role=USER");
        }
    }

    /**
     * 管理员警告（F-ADMIN-002 / CR-209）：只通知、不改账号状态；目标不可为 SUPERADMIN 或已注销账号。
     *
     * <p>通知经 {@link UserWarnedEvent} 交给 notification 上下文监听——account 不直接注入
     * NotificationApplicationService，否则会与对方（它已依赖本类）形成构造器循环依赖。
     */
    @Transactional
    public void adminWarn(long operatorId, long targetId, String reason) {
        Account target = accountOf(targetId);
        if (target.getRole() == AccountRole.SUPERADMIN) {
            throw new ApiException(ResultCode.FORBIDDEN);
        }
        if (target.getStatus() == AccountStatus.DEACTIVATED) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
        auditService.record(operatorId, "USER_WARN", "users", targetId, "reason=" + reason);
        eventPublisher.publishEvent(new UserWarnedEvent(operatorId, targetId, reason));
    }

    /**
     * 管理员禁言（F-ADMIN-002 / CR-209）：写截止时间，到期自然失效；不吊销 token（登录与浏览不受影响，
     * 只有内容写入被 {@link #assertAllowedToWriteContent} 拦下）。
     *
     * @param hours 1~720（最长 30 天）；永久处置用封禁而不是禁言
     * @return 禁言截止时间，供调用方回显
     */
    @Transactional
    public Instant adminMute(long operatorId, long targetId, int hours, String reason) {
        if (hours < 1 || hours > 720) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
        Account target = accountOf(targetId);
        if (target.getRole() == AccountRole.SUPERADMIN || target.getStatus() != AccountStatus.ACTIVE) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
        Instant until = Instant.now().plus(Duration.ofHours(hours));
        accountRepository.updateMutedUntil(targetId, until);
        auditService.record(operatorId, "USER_MUTE", "users", targetId,
                "until=%s hours=%d reason=%s".formatted(until, hours, reason));
        eventPublisher.publishEvent(new UserMutedEvent(operatorId, targetId, until, reason));
        return until;
    }

    /** 解除禁言（F-ADMIN-002 / CR-209）：幂等；本来未禁言时不写库、不记审计 */
    @Transactional
    public void adminUnmute(long operatorId, long targetId) {
        Account target = accountOf(targetId);
        if (target.getRole() == AccountRole.SUPERADMIN) {
            throw new ApiException(ResultCode.FORBIDDEN);
        }
        if (target.getMutedUntil() == null) {
            return;
        }
        accountRepository.updateMutedUntil(targetId, null);
        auditService.record(operatorId, "USER_UNMUTE", "users", targetId, null);
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
