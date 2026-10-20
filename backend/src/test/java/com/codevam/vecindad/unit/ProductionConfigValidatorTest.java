package com.codevam.vecindad.unit;

import com.codevam.vecindad.config.AppProperties;
import com.codevam.vecindad.config.ProductionConfigValidator;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionConfigValidatorTest {

    private static final String JWT_SECRET =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void acceptsACompleteProductionConfiguration() {
        MockEnvironment env = secureEnvironment();
        ProductionConfigValidator validator = new ProductionConfigValidator(env, productionProps());

        assertDoesNotThrow(validator::afterPropertiesSet);
    }

    @Test
    void rejectsEveryInsecureProductionDefaultAtOnce() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("springdoc.api-docs.enabled", "true")
                .withProperty("spring.datasource.password", "change-me-postgres")
                .withProperty("spring.data.redis.password", "short");
        AppProperties props = new AppProperties(
                "", "",
                "production",
                "http://localhost:8080",
                new AppProperties.Mail(true, "not-an-email"),
                new AppProperties.Jwt("dev-only-secret", "vecindad", Duration.ofMinutes(15), Duration.ofDays(7)),
                new AppProperties.Security(5, Duration.ofMinutes(15), Duration.ofMinutes(30), Duration.ofHours(72),
                        10, 12, List.of("http://localhost:3000", "https://localhost:8443")),
                new AppProperties.Seed(true, "x"),
                new AppProperties.Bootstrap("", "", "Administrador de plataforma"),
                new AppProperties.Wompi(true, "pub_test_demo", "prv_test_demo", "test_events_secret",
                        "test_integrity_secret", "https://sandbox.wompi.co/v1", "http://localhost:8080/resultado"),
                new AppProperties.Storage("/tmp/vecindad-storage"),
                new AppProperties.Maintenance("0 30 3 * * *", 30));
        ProductionConfigValidator validator = new ProductionConfigValidator(env, props);

        IllegalStateException error = assertThrows(IllegalStateException.class, validator::afterPropertiesSet);
        String message = error.getMessage();
        assertTrue(message.contains("APP_NAME"));
        assertTrue(message.contains("APP_COMPANY"));
        assertTrue(message.contains("APP_PUBLIC_URL"));
        assertTrue(message.contains("WOMPI_REDIRECT_URL"));
        assertTrue(message.contains("APP_SEED_ENABLED"));
        assertTrue(message.contains("MAIL_DEV_LOG_LINKS"));
        assertTrue(message.contains("SWAGGER_ENABLED"));
        assertTrue(message.contains("CORS_ALLOWED_ORIGINS"));
        assertTrue(message.contains("JWT_SECRET"));
        assertTrue(message.contains("DATABASE_PASSWORD"));
        assertTrue(message.contains("REDIS_PASSWORD"));
        assertTrue(message.contains("SMTP_HOST"));
        assertTrue(message.contains("MAIL_FROM"));
        assertTrue(message.contains("WOMPI_PUBLIC_KEY"));
        assertTrue(message.contains("WOMPI_API_URL"));
        assertTrue(message.contains("STORAGE_LOCAL_DIR"));
    }

    @Test
    void doesNotApplyProductionRulesOutsideProduction() {
        AppProperties props = new AppProperties(
                null, null, "development", null,
                new AppProperties.Mail(true, null),
                new AppProperties.Jwt(null, "vecindad", Duration.ofMinutes(15), Duration.ofDays(7)),
                new AppProperties.Security(5, Duration.ofMinutes(15), Duration.ofMinutes(30), Duration.ofHours(72),
                        10, 12, null),
                new AppProperties.Seed(true, "x"),
                new AppProperties.Bootstrap("", "", "Administrador de plataforma"),
                null, new AppProperties.Storage("/tmp/vecindad-storage"),
                new AppProperties.Maintenance("0 30 3 * * *", 30));

        assertDoesNotThrow(() -> new ProductionConfigValidator(new MockEnvironment(), props).afterPropertiesSet());
    }

    @Test
    void acceptsDisabledWompiWithoutProductionKeys() {
        AppProperties valid = productionProps();
        AppProperties withoutWompi = new AppProperties(
                valid.name(), valid.company(), valid.env(), valid.publicUrl(), valid.mail(), valid.jwt(),
                valid.security(), valid.seed(), valid.bootstrap(),
                new AppProperties.Wompi(false, null, null, null, null, null, null),
                valid.storage(), valid.maintenance());

        assertDoesNotThrow(() -> new ProductionConfigValidator(secureEnvironment(), withoutWompi).afterPropertiesSet());
    }

    private static MockEnvironment secureEnvironment() {
        return new MockEnvironment()
                .withProperty("springdoc.api-docs.enabled", "false")
                .withProperty("spring.datasource.password", "DatabaseSecret-2026!")
                .withProperty("spring.data.redis.password", "RedisSecret-2026!")
                .withProperty("spring.mail.host", "smtp.example.com");
    }

    private static AppProperties productionProps() {
        return new AppProperties(
                "Vecindad", "CodeVam", "production", "https://app.example.com",
                new AppProperties.Mail(false, "no-reply@example.com"),
                new AppProperties.Jwt(JWT_SECRET, "vecindad", Duration.ofMinutes(15), Duration.ofDays(7)),
                new AppProperties.Security(5, Duration.ofMinutes(15), Duration.ofMinutes(30), Duration.ofHours(72),
                        10, 12, List.of("https://app.example.com", "https://localhost", "capacitor://localhost", "ionic://localhost")),
                new AppProperties.Seed(false, "x"),
                new AppProperties.Bootstrap("", "", "Administrador de plataforma"),
                new AppProperties.Wompi(false, null, null, null, null, null, null),
                new AppProperties.Storage(java.nio.file.Path.of("data", "vecindad-storage").toAbsolutePath().toString()),
                new AppProperties.Maintenance("0 30 3 * * *", 30));
    }
}
