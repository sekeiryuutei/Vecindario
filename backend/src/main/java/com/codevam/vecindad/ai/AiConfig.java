package com.codevam.vecindad.ai;

import com.codevam.vecindad.config.AiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    public AiGateway aiGateway(AiProperties props, ObjectMapper mapper) {
        return new RoutingAiGateway(props,
                new GeminiAiProvider(props, mapper),
                new OpenAiCompatibleProvider(props, mapper));
    }
}
