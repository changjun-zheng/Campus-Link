package com.campuslink.common.security;

import java.time.Instant;

/**
 * 用户级 token 吊销端口（CR-208）：把「某时刻之前签发的 JWT 全部作废」记为一个水位。
 *
 * <p>JWT 本身无状态，{@code iat} 精度为秒；因此比较也按秒——签发时间不晚于水位即视为已撤销。
 * 同一秒内「刚签发就被吊销」与「吊销后同一秒重新登录」无法区分，取向是宁可误伤新 token
 * （用户再登录一次即可），也不放过旧 token。水位只增不减，由 Redis TTL（JWT 有效期 + 1 小时）自然清理。
 *
 * <p>端口放在 {@code common}：写侧由 account 应用服务调用（注销 / 封禁 / 角色变更），
 * 读侧由全局 {@code JwtAuthenticationFilter} 调用，两边都不应依赖对方模块的内部类型。
 */
public interface TokenRevocationGateway {

    /** 撤销该用户在 {@code cutoff} 之前签发的全部 token。 */
    void revokeAllBefore(long userId, Instant cutoff);

    /** 该用户在 {@code issuedAt} 签发的 token 是否已在水位之前。 */
    boolean isRevoked(long userId, Instant issuedAt);
}
