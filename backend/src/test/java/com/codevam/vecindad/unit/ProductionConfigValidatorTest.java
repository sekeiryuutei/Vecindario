package com.codevam.vecindad.unit;

import com.codevam.vecindad.config.AiProperties;
import com.codevam.vecindad.config.AppProperties;
import com.codevam.vecindad.config.ProductionConfigValidator;
import com.codevam.vecindad.config.WhatsAppProperties;
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
        ProductionConfigValidator validator = new ProductionConfigValidator(env, productionProps(),
                whatsappDisabled(), aiLocal());

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
        ProductionConfigValidator validator = new ProductionConfigValidator(env, props,
                new WhatsAppProperties(true, "", "", "v21.0", "", "", "https://graph.facebook.com", true, false),
                new AiProperties(true, "gemini", "", "gemini-3.7-flash",
                        "https://generativelanguage.googleapis.com", "", "", "https://api.openai.com/v1",
                        List.of("local"), 20));

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
        assertTrue(message.contains("WHATSAPP_VERIFY_TOKEN"));
        assertTrue(message.contains("WHATSAPP_ACCESS_TOKEN"));
        assertTrue(message.contains("GEMINI_API_KEY"));
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

        assertDoesNotThrow(() -> new ProductionConfigValidator(new MockEnvironment(), props,
                whatsappDisabled(), aiLocal()).afterPropertiesSet());
    }

    @Test
    void rejectsEqualWompiSecrets() {
        AppProperties valid = productionProps();
        AppProperties equalSecrets = new AppProperties(
                valid.name(), valid.company(), valid.env(), valid.publicUrl(), valid.mail(), valid.jwt(),
                valid.security(), valid.seed(), valid.bootstrap(),
                new AppProperties.Wompi(true, "pub_prod_example", "prv_prod_example", "same-secret",
                        "same-secret", "https://production.wompi.co/v1",
                        "https://app.example.com/pagos/wompi/resultado"),
                valid.storage(), valid.maintenance());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new ProductionConfigValidator(secureEnvironment(), equalSecrets,
                        whatsappDisabled(), aiLocal()).afterPropertiesSet());
        assertTrue(error.getMessage().contains("WOMPI_INTEGRITY_SECRET y WOMPI_EVENTS_SECRET"));
    }

    @Test
    void acceptsDisabledWompiWithoutProductionKeys() {
        AppProperties valid = productionProps();
        AppProperties withoutWompi = new AppProperties(
                valid.name(), valid.company(), valid.env(), valid.publicUrl(), valid.mail(), valid.jwt(),
                valid.security(), valid.seed(), valid.bootstrap(),
                new AppProperties.Wompi(false, null, null, null, null, null, null),
                valid.storage(), valid.maintenance());

        assertDoesNotThrow(() -> new ProductionConfigValidator(secureEnvironment(), withoutWompi,
                whatsappDisabled(), aiLocal()).afterPropertiesSet());
    }

    private static WhatsAppProperties whatsappDisabled() {
        return new WhatsAppProperties(false, null, null, "v21.0", null, null,
                "https://graph.facebook.com", false, false);
    }

    private static AiProperties aiLocal() {
        return new AiProperties(false, "local", null, "gemini-3.7-flash",
                "https://generativelanguage.googleapis.com", null, "gpt-4o-mini",
                "https://api.openai.com/v1", List.of("local"), 20);
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
