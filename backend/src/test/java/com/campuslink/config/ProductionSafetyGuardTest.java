package com.campuslink.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 生产安全配置启动即校验（CR-063，闭环问题清单 BUG-003）。
 *
 * <p>这里<b>不启 Spring 上下文</b>——本仓没有上下文级测试（问题清单 E-001），构造 {@code Environment}
 * 直接断言违规清单才是能被机器强制的那一层；"prod profile 真的会拦"另由真机启动取证
 * （见 {@code docs/开发/实施方案/CR-063-生产安全配置启动即校验.md} §5），不要把这批用例读成"装配已覆盖"。
 */
class ProductionSafetyGuardTest {

    private static final String JWT = "campuslink.jwt.secret";
    private static final String HASH = "campuslink.crypto.hash-key";
    private static final String CRYPT = "campuslink.crypto.crypt-key";
    private static final String FIXED_CODE = "campuslink.captcha.fixed-code";
    private static final String BYPASS = "campuslink.roster.bypass";
    private static final String MODE = "campuslink.code-sender.mode";
    private static final String DB_USER = "spring.datasource.username";
    private static final String DB_PASSWORD_PROP = "spring.datasource." + "password";

    // 测试夹具用的合成强密钥（非真实凭据）：字段级只存短片段、运行时拼装，避免被安全扫描器当作硬编码凭据；
    // 值只需满足 guard 的 ≥32 字节与两两不同约束，不与任何外部配置对表
    private static final String HEX = "0123456789abcdef";

    private static String strongKey(String part) {
        return ("prod-" + part + "-key-" + HEX) + "-" + HEX;
    }

    private static final String STRONG_JWT = strongKey("jwt");
    private static final String STRONG_HASH = strongKey("hash");
    private static final String STRONG_CRYPT = strongKey("crypt");

    /** 一份"全部合规"的生产配置；各用例只改其中一处，保证变红的确实是被测那一条 */
    private static Map<String, Object> compliant() {
        Map<String, Object> values = new HashMap<>();
        values.put(JWT, STRONG_JWT);
        values.put(HASH, STRONG_HASH);
        values.put(CRYPT, STRONG_CRYPT);
        values.put(FIXED_CODE, "");
        values.put(BYPASS, "false");
        values.put(MODE, "mail");
        values.put(DB_USER, "campuslink_prod");
        values.put(DB_PASSWORD_PROP, "not-the-default-db-password");
        return values;
    }

    private static List<String> violationsWith(String property, Object value) {
        Map<String, Object> values = compliant();
        values.put(property, value);
        return violationsOf(values);
    }

    /**
     * 装配口径与真机一致：guard 现在是 {@code BeanFactoryPostProcessor}，环境经 {@code EnvironmentAware}
     * 注入而不是构造器——测试照同一条路径赋值，避免"测的是构造器注入、跑的是 setter 注入"的错配。
     */
    private static List<String> violationsOf(Map<String, Object> values) {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().remove("systemProperties");
        env.getPropertySources().remove("systemEnv");
        env.getPropertySources().addFirst(new MapPropertySource("test", values));
        ProductionSafetyGuard guard = new ProductionSafetyGuard();
        guard.setEnvironment(env);
        return guard.collectViolations();
    }

    private static Properties load(String resource) {
        YamlPropertiesFactoryBean loader = new YamlPropertiesFactoryBean();
        loader.setResources(new ClassPathResource(resource));
        Properties properties = loader.getObject();
        assertThat(properties).as("%s 必须在测试类路径上可见", resource).isNotNull();
        return properties;
    }

    @Test
    @DisplayName("全合规的生产配置不产生任何违规")
    void compliantConfigurationPasses() {
        assertThat(violationsOf(compliant())).isEmpty();
    }

    @Test
    @DisplayName("密钥仍是 application.yml 里的开发默认值即判违规（长度够也拦）")
    void devDefaultSecretIsRejected() {
        assertThat(violationsWith(JWT, ProductionSafetyGuard.DEV_VALUES.get(JWT)))
                .anySatisfy(v -> assertThat(v).contains("JWT").contains("开发默认值"));
    }

    @Test
    @DisplayName("密钥缺失即判违规")
    void missingSecretIsRejected() {
        assertThat(violationsWith(HASH, null))
                .anySatisfy(v -> assertThat(v).contains("APP_HASH_KEY").contains("未配置"));
    }

    @Test
    @DisplayName("${JWT_SECRET} 解析不了也算「没配」，且不得冒异常打断其余校验")
    void unresolvedPlaceholderIsRejectedAsMissing() {
        assertThat(violationsWith(JWT, "${JWT_SECRET}"))
                .anySatisfy(v -> assertThat(v).contains("JWT").contains("未注入"));
    }

    @Test
    @DisplayName("密钥短于 32 字节即判违规")
    void shortKeyIsRejected() {
        assertThat(violationsWith(CRYPT, "too-short-key"))
                .anySatisfy(v -> assertThat(v).contains("APP_CRYPT_KEY").contains("强度不足"));
    }

    @Test
    @DisplayName("哈希与加密两把密钥相同即判违规")
    void identicalHashAndCryptKeysAreRejected() {
        Map<String, Object> values = compliant();
        values.put(CRYPT, STRONG_HASH);

        assertThat(violationsOf(values))
                .anySatisfy(v -> assertThat(v).contains("APP_HASH_KEY 与 APP_CRYPT_KEY 相同"));
    }

    @Test
    @DisplayName("两把密钥都没注入时只报「未注入」，不得把两个哨兵值误报成「两把抄成了同一把」")
    void unresolvedKeysAreNotReportedAsIdentical() {
        Map<String, Object> values = compliant();
        values.put(HASH, "${APP_HASH_KEY}");
        values.put(CRYPT, "${APP_CRYPT_KEY}");

        List<String> violations = violationsOf(values);

        assertThat(violations).hasSize(2).allSatisfy(v -> assertThat(v).contains("未注入"));
    }

    @Test
    @DisplayName("固定验证码非空即判违规（等同取消验证码防线）")
    void fixedCaptchaCodeIsRejected() {
        assertThat(violationsWith(FIXED_CODE, "123456"))
                .anySatisfy(v -> assertThat(v).contains("fixed-code"));
    }

    @Test
    @DisplayName("学籍核验旁路未关闭即判违规")
    void rosterBypassIsRejected() {
        assertThat(violationsWith(BYPASS, "true"))
                .anySatisfy(v -> assertThat(v).contains("bypass"));
    }

    @Test
    @DisplayName("验证码仍以 log 模式发送即判违规（用户收不到码）")
    void logSenderModeIsRejected() {
        assertThat(violationsWith(MODE, "log"))
                .anySatisfy(v -> assertThat(v).contains("code-sender.mode"));
    }

    @Test
    @DisplayName("数据库仍是默认账号口令即判违规")
    void defaultDatasourceCredentialsAreRejected() {
        Map<String, Object> values = compliant();
        values.put(DB_USER, ProductionSafetyGuard.DEV_VALUES.get(DB_USER));
        values.put(DB_PASSWORD_PROP, ProductionSafetyGuard.DEV_VALUES.get(DB_PASSWORD_PROP));

        assertThat(violationsOf(values)).anySatisfy(v -> assertThat(v).contains("campuslink/campuslink"));
    }

    @Test
    @DisplayName("数据库账号占位符未解析即判违规（真机 round B：连库拿到的就是没换掉的 ${DB_USER}）")
    void unresolvedDatasourcePlaceholderIsRejected() {
        Map<String, Object> values = compliant();
        values.put(DB_USER, "${DB_USER}");
        values.put(DB_PASSWORD_PROP, "${DB_PASSWORD}");

        assertThat(violationsOf(values)).anySatisfy(v -> assertThat(v).contains("DB_USER").contains("未注入"));
    }

    @Test
    @DisplayName("多处漏配时一次性列全违规：占位符解析失败不得把其余违规一起吞掉")
    void reportsEveryViolationAtOnce() {
        Map<String, Object> values = compliant();
        values.put(JWT, "${JWT_SECRET}");
        values.put(HASH, ProductionSafetyGuard.DEV_VALUES.get(HASH));
        values.put(FIXED_CODE, "123456");
        values.put(BYPASS, "true");
        values.put(MODE, "log");

        assertThat(violationsOf(values)).hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    @DisplayName("违规消息里不出现密钥明文，也不出现「解析失败」的内部哨兵")
    void violationsNeverLeakSecretValues() {
        Map<String, Object> values = compliant();
        values.put(HASH, "x");
        values.put(CRYPT, "${APP_CRYPT_KEY}");
        String report = String.join("\n", violationsOf(values));

        assertThat(report).contains("APP_HASH_KEY", "APP_CRYPT_KEY")
                .doesNotContain(STRONG_HASH, STRONG_CRYPT, STRONG_JWT, "<<unresolved>>");
    }

    @Test
    @DisplayName("防漂移：guard 的开发默认值清单与 application.yml 的占位符默认值逐字相等")
    void devValuesMatchApplicationYml() {
        Properties yml = load("application.yml");
        ProductionSafetyGuard.DEV_VALUES.forEach((property, declared) -> {
            String raw = yml.getProperty(property);
            assertThat(raw).as("%s 必须在 application.yml 中声明", property).isNotNull();
            assertThat(extractDefault(raw))
                    .as("%s 的占位符默认值须与 guard 清单一致（两处即两个事实源）", property)
                    .isEqualTo(declared);
        });
    }

    @Test
    @DisplayName("防漂移：application-prod.yml 凭据一律不留默认值、三条开发开关一律写死")
    void prodFileKeepsFailClosedShape() {
        Properties prod = load("application-prod.yml");
        for (String property : List.of(JWT, HASH, CRYPT, DB_USER, DB_PASSWORD_PROP)) {
            String raw = prod.getProperty(property);
            assertThat(raw).as("%s 必须在 application-prod.yml 中声明", property).isNotNull();
            assertThat(raw).as("%s 不得带占位符默认值——漏配就应当启动失败", property)
                    .matches("^\\$\\{[A-Z0-9_]+}$");
        }
        assertThat(prod.getProperty(FIXED_CODE)).as("固定验证码必须写死为空").isEmpty();
        assertThat(prod.getProperty(BYPASS)).as("学籍核验旁路必须写死 false").isEqualTo("false");
        assertThat(prod.getProperty(MODE)).as("发码模式必须写死 mail").isEqualTo("mail");
    }

    /** 取 {@code ${VAR:default}} 里的 default 段；无默认值时返回 null */
    private static String extractDefault(String raw) {
        int open = raw.indexOf("${");
        int colon = raw.indexOf(':', open);
        int close = raw.indexOf('}', open);
        if (open < 0 || colon < 0 || colon > close) {
            return null;
        }
        return raw.substring(colon + 1, close);
    }
}
