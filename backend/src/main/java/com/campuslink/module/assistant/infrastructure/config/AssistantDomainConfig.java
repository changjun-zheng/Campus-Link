package com.campuslink.module.assistant.infrastructure.config;

import com.campuslink.module.assistant.domain.service.AssistantPromptBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 领域服务装配（CR-200）：domain 不挂 Spring 注解，由 infrastructure 组装。 */
@Configuration
public class AssistantDomainConfig {

    @Bean
    public AssistantPromptBuilder assistantPromptBuilder() {
        return new AssistantPromptBuilder();
    }
}
