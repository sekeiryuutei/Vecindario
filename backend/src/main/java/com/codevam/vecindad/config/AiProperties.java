package com.codevam.vecindad.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Proveedor de IA desacoplado. El proveedor principal puede fallar y el
 * enrutador intenta los de respaldo en orden; {@code local} siempre responde
 * con un mensaje seguro sin consumir tokens.
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        boolean enabled,
        String provider,
        String geminiApiKey,
        String geminiModel,
        String geminiBaseUrl,
        String openaiApiKey,
        String openaiModel,
        String openaiBaseUrl,
        List<String> fallbackProviders,
        int timeoutSeconds
) {
}
