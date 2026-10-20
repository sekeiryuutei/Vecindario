package com.codevam.vecindad.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String name, String company, String env, String publicUrl,
                            Mail mail, Jwt jwt, Security security, Seed seed, Bootstrap bootstrap,
                            Wompi wompi, Storage storage, Maintenance maintenance) {

    public record Mail(boolean devLogLinks, String from) {}

    public record Jwt(String secret, String issuer, Duration accessExpiration, Duration refreshExpiration) {}

    public record Security(int maxFailedAttempts, Duration lockDuration, Duration resetTokenTtl,
                           Duration invitationTtl, int loginRateLimitPerMinute, int bcryptStrength,
                           List<String> corsAllowedOrigins) {}

    public record Seed(boolean enabled, String password) {}

    /** Creación idempotente del primer superadministrador de plataforma en instalaciones sin seed. */
    public record Bootstrap(String email, String password, String name) {}

    public record Wompi(boolean enabled, String publicKey, String privateKey, String eventsSecret,
                        String integritySecret, String apiUrl, String redirectUrl) {}

    public record Storage(String localDir) {}

    public record Maintenance(String tokenCleanupCron, int tokenRetentionDays) {}

    public boolean isProduction() {
        return "production".equalsIgnoreCase(env);
    }
}
