package com.codevam.vecindad.ai;

import com.codevam.vecindad.config.AiProperties;
import com.codevam.vecindad.integrations.TenantIntegrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    public AiGateway aiGateway(AiProperties props, TenantIntegrationService integrations, ObjectMapper mapper) {
        return new TenantAiGateway(props, integrations, mapper);
    }
}
