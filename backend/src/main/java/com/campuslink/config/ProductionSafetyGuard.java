package com.campuslink.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.PlaceholderResolutionException;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * 生产安全配置启动即校验（CR-063，闭环问题清单 BUG-003）。
 *
 * <p><b>要挡的是哪一类错法</b>：本仓此前没有任何 profile / 环境判定，{@code application.yml} 里
 * {@code ${VAR:dev-default}} 的默认值就是唯一生效值——部署时漏配一个环境变量，应用照常启动、
 * 只打一行 WARN，于是系统"看起来在跑"，实际跑在"JWT 用仓库里的密钥 / 验证码恒为固定值 /
 * 学籍核验旁路开着"的弱安全模式。
 *
 * <p><b>与 {@code CryptoService.requireKey} 的分工</b>：那处只断言"≥32 字节"，而三个开发默认值
 * 长度都够，所以它挡不住"给了值、但给的是仓库里的开发字面量"。本类专门挡这一种，
 * 两道防线各挡一类错法，不重复表达同一条件。
 *
 * <p><b>为什么是 {@code BeanFactoryPostProcessor} 而不是 {@code InitializingBean}</b>：真机取证发现，
 * 普通 bean 的初始化排在数据源、Flyway 与 {@code JwtService} 之后——prod 漏配时先炸的是
 * {@code WeakKeyException} 或 {@code Access denied for user '${DB_USER}'}，守卫根本轮不到说话。
 * BFPP 在常规 bean 之前实例化，才把校验真正提到链路最前。
 *
 * <p><b>顺带取证到的一个反直觉事实</b>：{@code ${JWT_SECRET}} 这类<b>无默认值</b>的占位符，两条读取路径
 * 行为不一致——{@code env.getProperty(key)} 是<b>严格</b>解析，解析不了直接抛
 * {@link org.springframework.util.PlaceholderResolutionException}；而 Boot 绑定 {@code @ConfigurationProperties}
 * （数据源就走这条路径）是<b>非严格</b>解析，解析不了就把字面量原样注入，于是真机 round B 报的是
 * {@code Access denied for user '${DB_USER}'} 而不是"启动中止"。所以"不留默认值"只是让值变得可疑，
 * 拦下它仍要靠本类：{@link #value} 捕获那个异常、把"解析不了"折成一条可读违规，
 * 从而保住"一次列全违规"而不是"改一条重启一次"。
 *
 * <p>只在 {@code prod} profile 下装配：开发期默认行为一条未放宽也未收紧。
 */
@Component
@Profile("prod")
public class ProductionSafetyGuard implements BeanFactoryPostProcessor, EnvironmentAware {

    private static final Logger log = LoggerFactory.getLogger(ProductionSafetyGuard.class);

    /** 签名与加密密钥的最短长度，与 {@code CryptoService.requireKey} 同口径 */
    private static final int MIN_KEY_BYTES = 32;

    /**
     * 占位符解析失败（环境变量没给、yml 又不留默认值）时的内部哨兵。取真实配置不会出现的字面量，
     * 只在 {@link #value} 与 {@link #isUnresolved} 之间流转，绝不写进违规消息或日志。
     */
    private static final String UNRESOLVED = "<<unresolved>>";

    /**
     * {@code application.yml} 中以"占位符默认值"形式声明的开发态取值——<b>这份清单必须与 yml 逐字相等</b>，
     * 由 {@code ProductionSafetyGuardTest} 读原文比对锁死（改 yml 默认值而不同步这里，测试即红）。
     */
    static final Map<String, String> DEV_VALUES = Map.ofEntries(
            Map.entry("campuslink.jwt.secret", "dev-only-jwt-secret-change-me-0123456789abcdef"),
            Map.entry("campuslink.crypto.hash-key", "dev-only-hash-key-change-me-32-bytes-minimum!"),
            Map.entry("campuslink.crypto.crypt-key", "dev-only-crypt-key-change-me-32-bytes-minimum!"),
            Map.entry("campuslink.captcha.fixed-code", "123456"),
            Map.entry("campuslink.roster.bypass", "true"),
            Map.entry("campuslink.code-sender.mode", "log"),
            Map.entry("spring.datasource.username", "campuslink"),
            Map.entry("spring.datasource.password", "campuslink"));

    private Environment env;

    @Override
    public void setEnvironment(Environment environment) {
        this.env = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        List<String> violations = collectViolations();
        if (!violations.isEmpty()) {
            // 一次列全违规：运维一轮就能改完，而不是"改一个重启一次"逐个试错
            throw new IllegalStateException("生产安全配置校验未通过（" + violations.size() + " 项）:\n  - "
                    + String.join("\n  - ", violations)
                    + "\n请为对应环境变量赋值后用 prod profile 重新启动。");
        }
        log.info("生产安全配置校验通过：{} 项（密钥指纹 jwt={} hash={} crypt={}）", checkedItemCount(),
                fingerprint(value("campuslink.jwt.secret")), fingerprint(value("campuslink.crypto.hash-key")),
                fingerprint(value("campuslink.crypto.crypt-key")));
    }

    /** 校验项数写出来是为了让日志能证明"它真的跑过、且不是一条空规则"；Meili 开关打开时多一项 */
    private int checkedItemCount() {
        return "meilisearch".equalsIgnoreCase(value("campuslink.search.engine")) ? 10 : 9;
    }

    /** 供测试直接断言，不经 Spring 生命周期 */
    List<String> collectViolations() {
        List<String> violations = new ArrayList<>();
        checkKey(violations, "campuslink.jwt.secret", "JWT 签名密钥（环境变量 JWT_SECRET）");
        checkKey(violations, "campuslink.crypto.hash-key", "HMAC 密钥（环境变量 APP_HASH_KEY）");
        checkKey(violations, "campuslink.crypto.crypt-key", "AES 密钥（环境变量 APP_CRYPT_KEY）");
        checkDistinctKeys(violations);
        checkFixedCode(violations);
        checkRosterBypass(violations);
        checkCodeSenderMode(violations);
        checkDatasourceDefaults(violations);
        checkSearchEngine(violations);
        violations.addAll(MailConfigurationGuard.violations(env));
        return violations;
    }

    /**
     * 搜索切到 MeiliSearch 时强制要求 API key（CR-181 / ADR-004）：本机开发可以不开 master key，
     * 但生产实例若直接暴露无鉴权的搜索服务，任何人都能读写索引。engine=mysql（缺省）时本项不生效。
     */
    private void checkSearchEngine(List<String> violations) {
        if (!"meilisearch".equalsIgnoreCase(value("campuslink.search.engine"))) {
            return;
        }
        String apiKey = value("campuslink.search.meili.api-key");
        if (!StringUtils.hasText(apiKey) || isUnresolved(apiKey)) {
            violations.add("campuslink.search.engine=meilisearch 但 MEILI_API_KEY 未配置：搜索服务对任意调用者开放");
        }
    }

    private void checkKey(List<String> violations, String property, String label) {
        String current = value(property);
        if (!StringUtils.hasText(current)) {
            violations.add(label + " 未配置");
            return;
        }
        if (isUnresolved(current)) {
            // 真机取证：prod 里 ${JWT_SECRET} 无默认值，环境变量没给时严格解析会抛——等价于"没配"
            violations.add(label + " 未注入：占位符无法解析，对应环境变量没有赋值");
            return;
        }
        if (current.equals(DEV_VALUES.get(property))) {
            violations.add(label + " 仍是 application.yml 里的开发默认值");
            return;
        }
        int bytes = current.getBytes(StandardCharsets.UTF_8).length;
        if (bytes < MIN_KEY_BYTES) {
            violations.add(label + " 强度不足：" + bytes + " 字节 < " + MIN_KEY_BYTES + " 字节");
        }
    }

    private void checkDistinctKeys(List<String> violations) {
        String hash = value("campuslink.crypto.hash-key");
        String crypt = value("campuslink.crypto.crypt-key");
        // 真机 round A 取证：两把密钥都没注入时，两个哨兵值"相等"会被误报成"两把抄成了同一把"，
        // 让人去查一个不存在的问题。缺失已由 checkKey 各自报过，这里只比较两个真实取到过的值。
        if (isComparable(hash) && isComparable(crypt) && hash.equals(crypt)) {
            violations.add("APP_HASH_KEY 与 APP_CRYPT_KEY 相同：等值查询哈希与落库加密共用一把密钥，泄露即同时失去两层防线");
        }
    }

    private boolean isComparable(String current) {
        return StringUtils.hasText(current) && !isUnresolved(current);
    }

    private void checkFixedCode(List<String> violations) {
        String fixed = value("campuslink.captcha.fixed-code");
        if (StringUtils.hasText(fixed)) {
            violations.add("campuslink.captcha.fixed-code 非空（CAPTCHA_FIXED_CODE）：固定验证码等同于取消验证码防线，生产必须留空");
        }
    }

    private void checkRosterBypass(List<String> violations) {
        String bypass = value("campuslink.roster.bypass");
        if (!"false".equalsIgnoreCase(bypass)) {
            violations.add("campuslink.roster.bypass 不为 false（APP_ROSTER_BYPASS）：学籍核验走内置测试名册，任何人可冒用名册身份注册");
        }
    }

    private void checkCodeSenderMode(List<String> violations) {
        String mode = value("campuslink.code-sender.mode");
        if (!"mail".equalsIgnoreCase(mode)) {
            violations.add("campuslink.code-sender.mode 不为 mail（CODE_SENDER_MODE）：验证码只写后端日志，用户收不到码、注册链路实际不可用");
        }
    }

    private void checkDatasourceDefaults(List<String> violations) {
        String user = value("spring.datasource.username");
        String password = value("spring.datasource.password");
        if (isUnresolved(user) || isUnresolved(password)) {
            violations.add("数据库账号或口令未注入：占位符无法解析（DB_USER / DB_PASSWORD 没有赋值）");
            return;
        }
        if (DEV_VALUES.get("spring.datasource.username").equals(user)
                && DEV_VALUES.get("spring.datasource.password").equals(password)) {
            violations.add("数据库仍是默认账号口令 campuslink/campuslink（DB_USER / DB_PASSWORD）");
        }
    }

    private boolean isUnresolved(String current) {
        return UNRESOLVED.equals(current);
    }

    /**
     * 读属性，并把"占位符解析不了"折成 {@link #UNRESOLVED} 而不是让异常冒出校验过程：
     * 冒出去就只报得出第一条违规，"一次列全"这条设计目标会静默失效。
     */
    private String value(String property) {
        try {
            return env.getProperty(property);
        } catch (PlaceholderResolutionException e) {
            return UNRESOLVED;
        }
    }

    /** 只输出不可逆指纹，绝不落密钥明文；前 8 位足够让人确认"这台实例用的是哪把密钥" */
    private String fingerprint(String secret) {
        if (!StringUtils.hasText(secret)) {
            return "未配置";
        }
        return digest(secret).substring(0, 8);
    }

    private static String digest(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
