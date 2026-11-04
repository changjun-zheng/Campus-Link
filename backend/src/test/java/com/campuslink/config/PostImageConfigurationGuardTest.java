package com.campuslink.config;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;
class PostImageConfigurationGuardTest {
    @Test void developmentRequiresReadingWhenUploading() {
        var env = new MockEnvironment();
        assertThat(PostImageConfigurationGuard.violations(env)).isEmpty();
        env.setProperty("campuslink.post-images.read-enabled", "false");
        assertThat(PostImageConfigurationGuard.violations(env)).isNotEmpty();
    }
    @Test void productionRejectsEitherOverride() {
        var env = new MockEnvironment(); env.setActiveProfiles("prod");
        for (String property : new String[]{"upload-enabled", "read-enabled"}) {
            env.setProperty("campuslink.post-images.upload-enabled", "false");
            env.setProperty("campuslink.post-images.read-enabled", "false");
            assertThat(PostImageConfigurationGuard.violations(env)).isEmpty();
            env.setProperty("campuslink.post-images." + property, "true");
            assertThat(PostImageConfigurationGuard.violations(env)).isNotEmpty();
        }
    }
}
