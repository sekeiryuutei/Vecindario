package com.codevam.vecindad.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String name, String company, String env, Mail mail, Jwt jwt,
                            Security security, Seed seed) {

    public record Mail(boolean devLogLinks) {}

    public record Jwt(String secret, String issuer, Duration accessExpiration, Duration refreshExpiration) {}

    public record Security(int maxFailedAttempts, Duration lockDuration, Duration resetTokenTtl,
                           Duration invitationTtl, int loginRateLimitPerMinute, int bcryptStrength,
                           List<String> corsAllowedOrigins) {}

    public record Seed(boolean enabled, String password) {}

    public boolean isProduction() {
        return "production".equalsIgnoreCase(env);
    }
}
