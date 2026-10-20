package com.codevam.vecindad.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Integración con WhatsApp Cloud API. El número es único para la plataforma;
 * cada copropiedad decide si expone el canal a sus residentes.
 */
@ConfigurationProperties(prefix = "app.whatsapp")
public record WhatsAppProperties(
        boolean enabled,
        String verifyToken,
        String appSecret,
        String apiVersion,
        String accessToken,
        String phoneNumberId,
        String baseUrl,
        boolean aiRepliesEnabled,
        boolean includeFinancialData
) {
    public boolean isConfigured() {
        return enabled && notBlank(accessToken) && notBlank(phoneNumberId);
    }

    public boolean canVerifyWebhook() {
        return enabled && notBlank(verifyToken);
    }

    public boolean canValidateSignature() {
        return notBlank(appSecret);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
