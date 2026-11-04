package com.campuslink.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Validate the effective mail configuration before normal beans; never opens a socket. */
@Component
public class MailConfigurationGuard implements BeanFactoryPostProcessor, EnvironmentAware {
    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        List<String> errors = violations(environment);
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Mail configuration invalid: " + String.join(", ", errors));
        }
    }

    static List<String> violations(Environment env) {
        List<String> errors = new ArrayList<>();
        String mode = value(env, "campuslink.code-sender.mode", "log");
        if (!"mail".equalsIgnoreCase(mode) && !"log".equalsIgnoreCase(mode)) {
            errors.add("campuslink.code-sender.mode");
        }
        if (!"mail".equalsIgnoreCase(mode)) {
            return errors;
        }
        for (String key : List.of("spring.mail.host", "spring.mail.username", "spring.mail.password",
                "campuslink.code-sender.from")) {
            String field = value(env, key, "");
            if (field.isBlank() || field.contains("${") || field.contains("\r") || field.contains("\n")) {
                errors.add(key);
            }
        }
        String from = value(env, "campuslink.code-sender.from", "");
        if (!validFrom(from)) {
            errors.add("campuslink.code-sender.from");
        }
        boundedInteger(env, errors, "spring.mail.port", 65535);
        boundedInteger(env, errors, "spring.mail.properties.mail.smtp.connectiontimeout", 3000);
        boundedInteger(env, errors, "spring.mail.properties.mail.smtp.timeout", 5000);
        boundedInteger(env, errors, "spring.mail.properties.mail.smtp.writetimeout", 5000);
        boolean starttls = "true".equals(value(env, "spring.mail.properties.mail.smtp.starttls.enable", ""));
        boolean ssl = "true".equals(value(env, "spring.mail.properties.mail.smtp.ssl.enable", ""));
        for (String key : List.of("spring.mail.properties.mail.smtp.starttls.enable",
                "spring.mail.properties.mail.smtp.ssl.enable")) {
            if (!List.of("true", "false").contains(value(env, key, ""))) {
                errors.add(key);
            }
        }
        if (starttls == ssl) {
            errors.add("spring.mail.properties.mail.smtp.starttls.enable / mail.smtp.ssl.enable");
        }
        if (starttls) {
            require(env, errors, "spring.mail.properties.mail.smtp.starttls.required", "true");
        }
        require(env, errors, "spring.mail.protocol", "smtp");
        require(env, errors, "spring.mail.properties.mail.smtp.auth", "true");
        require(env, errors, "spring.mail.properties.mail.smtp.ssl.checkserveridentity", "true");
        require(env, errors, "spring.mail.test-connection", "false");
        require(env, errors, "spring.mail.properties.mail.debug", "false");
        if (!value(env, "spring.mail.properties.mail.smtp.ssl.trust", "").isBlank()) {
            errors.add("spring.mail.properties.mail.smtp.ssl.trust");
        }
        return errors;
    }

    private static boolean validFrom(String from) {
        if (!from.matches("[^\\s<>@]+@[^\\s<>@]+")) {
            return false;
        }
        try {
            new jakarta.mail.internet.InternetAddress(from, true).validate();
            return true;
        } catch (jakarta.mail.internet.AddressException ex) {
            return false;
        }
    }

    private static void require(Environment env, List<String> errors, String key, String expected) {
        if (!expected.equals(value(env, key, ""))) {
            errors.add(key);
        }
    }

    private static void boundedInteger(Environment env, List<String> errors, String key, int maximum) {
        try {
            int number = Integer.parseInt(value(env, key, ""));
            if (number < 1 || number > maximum) {
                errors.add(key);
            }
        } catch (NumberFormatException ex) {
            errors.add(key);
        }
    }

    private static String value(Environment env, String key, String fallback) {
        try {
            return env.getProperty(key, fallback);
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }
}
