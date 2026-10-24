package com.campuslink.module.account.domain.gateway;

import com.campuslink.module.account.domain.exception.AccountConflictException;
import com.campuslink.module.account.domain.model.Account;
import com.campuslink.module.account.domain.model.AccountStatus;
import com.campuslink.module.account.domain.model.ProfileField;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 聚合仓储端口（Repository 模式）：账号聚合的持久化抽象，
 * MyBatis-Plus 实现位于 infrastructure/persistence。
 */
public interface AccountRepository {

    /**
     * 保存新聚合，返回带持久化 id 的聚合实例。
     *
     * @throws AccountConflictException 邮箱或学号已被占用（唯一键冲突）。
     *         适配器负责把数据库层异常翻译为该领域异常，应用层无需感知 JDBC / MyBatis 异常类型（DIP）。
     */
    Account save(Account account);

    /**
     * 资料编辑（F-ACC-007a）：**定向单列 UPDATE**，只 SET {@code changed} 里那几列。
     *
     * <p>为什么不复用 {@link #save} 整行回写：聚合是从"读那一刻"的状态改来的，整行写会把
     * {@code role} / {@code status} / {@code email_*} / {@code verified} 一起盖回去——并发窗口内
     * 平台侧对这些列的改动（封禁、名册处置）会被静默抹掉。这条约定与帖子侧
     * {@code updateStatus} / {@code updateHotScore} 走定向 SQL 是同一个理由。
     *
     * <p>空集合由适配器短路（不发 SQL）：幂等提交不该推进 {@code updated_at}。
     */
    void updateProfile(Long accountId, Account account, Set<ProfileField> changed);

    boolean existsByEmailHash(String emailHash);

    /** 手机号占用预检（CR-093 / F-011）：HMAC 哈希等值查询，与邮箱侧同规矩 */
    boolean existsByPhoneHash(String phoneHash);

    Optional<Account> findByEmailHash(String emailHash);

    /** 手机号登录查找（CR-093 / F-011）：HMAC 哈希等值查询 */
    Optional<Account> findByPhoneHash(String phoneHash);

    Optional<Account> findById(Long id);

    /** 批量按 id 查询（跨上下文读昵称的唯一入口，一次查询不做 N+1）；不存在的 id 不返回 */
    List<Account> findByIds(Collection<Long> ids);

    /**
     * 账号状态定向 UPDATE（CR-082 治理：封禁 / 注销 / 撤销注销共用），同时写 delete_at
     * （非注销场景传 null 即清空）。与 updateProfile 同理走定向 SQL，不整行回写。
     */
    void updateStatus(Long accountId, AccountStatus status, java.time.Instant deleteAt);

    /**
     * 匿名化（CR-082 注销冷静期满）：清 email / phone / 学号（_enc 与 _hash）、头像，
     * 昵称置占位文案，anonymized = true。不可逆操作，仅对「冷静期满且未撤销」的账号调用。
     */
    void anonymize(Long accountId, String placeholderNickname);

    /** 冷静期已满且尚未匿名化的注销账号（status=DEACTIVATED AND 0 < delete_at <= now AND anonymized = 0） */
    List<Account> findExpiredDeactivations(java.time.Instant now);

    /**
     * 运营看板（F-ADMIN-005 / CR-131）：按 {@code users.status} 分组的只读计数。
     *
     * <p>只返回库里实际出现的状态键；调用方用 {@code getOrDefault(..., 0L)} 补齐。
     * {@code DEACTIVATED} 同样计入总数（注销账号仍是一条账号记录），由调用方单列。
     */
    Map<AccountStatus, Long> countByStatus();
}
