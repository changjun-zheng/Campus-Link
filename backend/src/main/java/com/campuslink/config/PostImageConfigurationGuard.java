package com.campuslink.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class PostImageConfigurationGuard implements BeanFactoryPostProcessor, EnvironmentAware {
    private Environment environment;
    @Override public void setEnvironment(Environment environment) { this.environment = environment; }
    @Override public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        var errors = violations(environment);
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Post image configuration invalid: " + String.join(",", errors));
        }
    }

    static List<String> violations(Environment env) {
        List<String> errors = new ArrayList<>();
        boolean upload = env.getProperty("campuslink.post-images.upload-enabled", Boolean.class, true);
        boolean read = env.getProperty("campuslink.post-images.read-enabled", Boolean.class, true);
        boolean prod = env.matchesProfiles("prod");
        if (upload && !read || prod && upload) {
            errors.add("campuslink.post-images.upload-enabled");
        }
        if (prod && read) {
            errors.add("campuslink.post-images.read-enabled");
        }
        return errors;
    }
}
