package com.campuslink.config;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class MailConfigurationGuardTest {
    static Map<String, Object> compliant() {
        Map<String, Object> values = new HashMap<>();
        values.put("spring.mail.host", "smtp.example.invalid");
        values.put("spring.mail.port", "587");
        values.put("spring.mail.username", "test-user");
        values.put("spring.mail.password", java.util.UUID.randomUUID().toString());
        values.put("campuslink.code-sender.from", "noreply@example.invalid");
        values.put("spring.mail.protocol", "smtp");
        values.put("spring.mail.test-connection", "false");
        values.put("spring.mail.properties.mail.debug", "false");
        values.put("spring.mail.properties.mail.smtp.auth", "true");
        values.put("spring.mail.properties.mail.smtp.starttls.enable", "true");
        values.put("spring.mail.properties.mail.smtp.starttls.required", "true");
        values.put("spring.mail.properties.mail.smtp.ssl.enable", "false");
        values.put("spring.mail.properties.mail.smtp.ssl.checkserveridentity", "true");
        values.put("spring.mail.properties.mail.smtp.connectiontimeout", "3000");
        values.put("spring.mail.properties.mail.smtp.timeout", "5000");
        values.put("spring.mail.properties.mail.smtp.writetimeout", "5000");
        return values;
    }

    private MockEnvironment environment() {
        var env = new MockEnvironment();
        env.getPropertySources().addLast(new MapPropertySource("fixture", compliant()));
        env.setProperty("campuslink.code-sender.mode", "mail");
        return env;
    }

    @Test
    void validTlsModesPassWithoutNetwork() {
        var env = environment();
        assertThat(MailConfigurationGuard.violations(env)).isEmpty();
        env.setProperty("spring.mail.properties.mail.smtp.starttls.enable", "false");
        env.setProperty("spring.mail.properties.mail.smtp.ssl.enable", "true");
        env.setProperty("spring.mail.port", "465");
        assertThat(MailConfigurationGuard.violations(env)).isEmpty();
    }

    @Test
    void missingFieldsAndInvalidTlsFailWithoutLeakingValues() {
        var env = environment();
        env.setProperty("campuslink.code-sender.mode", "MAIL");
        env.setProperty("spring.mail.host", "");
        env.setProperty("spring.mail.password", "${MISSING_MAIL_PASSWORD}");
        env.setProperty("spring.mail.properties.mail.smtp.ssl.enable", "true");
        env.setProperty("spring.mail.properties.mail.smtp.timeout", "0");
        env.setProperty("campuslink.code-sender.from", "private@invalid,other");
        assertThat(String.join(",", MailConfigurationGuard.violations(env)))
                .contains("spring.mail.host", "spring.mail.password", "mail.smtp.ssl.enable", "mail.smtp.timeout",
                        "campuslink.code-sender.from")
                .doesNotContain("MISSING_MAIL_PASSWORD", "private@invalid,other");
    }

    @Test
    void unsafeEffectiveOverridesAreRejected() {
        for (String key : new String[] {"spring.mail.properties.mail.smtp.starttls.required",
                "spring.mail.properties.mail.smtp.ssl.checkserveridentity",
                "spring.mail.properties.mail.smtp.auth"}) {
            var env = environment();
            env.setProperty(key, "false");
            assertThat(MailConfigurationGuard.violations(env)).contains(key);
        }
        var env = environment();
        env.setProperty("spring.mail.properties.mail.debug", "true");
        env.setProperty("spring.mail.test-connection", "true");
        env.setProperty("spring.mail.properties.mail.smtp.ssl.trust", "*");
        assertThat(MailConfigurationGuard.violations(env)).hasSize(3);
    }

    @Test
    void logModeDoesNotRequireSmtpAndBadMailModeFailsContext() {
        new ApplicationContextRunner().withUserConfiguration(MailConfigurationGuard.class)
                .run(ctx -> assertThat(ctx).hasNotFailed());
        new ApplicationContextRunner().withUserConfiguration(MailConfigurationGuard.class)
                .withPropertyValues("campuslink.code-sender.mode=mail")
                .run(ctx -> assertThat(ctx).hasFailed());
        new ApplicationContextRunner().withUserConfiguration(MailConfigurationGuard.class)
                .withPropertyValues("campuslink.code-sender.mode=typo")
                .run(ctx -> assertThat(ctx).hasFailed());
    }
}
