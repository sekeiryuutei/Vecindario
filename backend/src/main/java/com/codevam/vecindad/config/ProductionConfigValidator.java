package com.codevam.vecindad.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Falla el arranque en producción ante configuraciones inseguras o incompletas.
 * Es preferible un contenedor que no arranca a uno que arranca con secretos de
 * desarrollo, seed activo, Swagger público o correo sin configurar.
 */
@Component
public class ProductionConfigValidator implements InitializingBean {

    private final Environment env;
    private final AppProperties props;

    public ProductionConfigValidator(Environment env, AppProperties props) {
        this.env = env;
        this.props = props;
    }

    @Override
    public void afterPropertiesSet() {
        if (!props.isProduction()) {
            return;
        }

        List<String> errors = new ArrayList<>();
        require(errors, StringUtils.hasText(props.name()), "APP_NAME es obligatorio.");
        require(errors, StringUtils.hasText(props.company()), "APP_COMPANY es obligatorio.");

        String publicUrl = props.publicUrl();
        require(errors, isHttps(publicUrl), "APP_PUBLIC_URL debe ser una URL HTTPS pública (no localhost).");
        String redirect = props.wompi() == null ? null : props.wompi().redirectUrl();
        if (props.wompi() != null && props.wompi().enabled()) {
            require(errors, isHttps(redirect), "WOMPI_REDIRECT_URL debe ser una URL HTTPS pública.");
        }

        if (props.seed() == null || props.seed().enabled()) {
            errors.add("APP_SEED_ENABLED debe ser false en producción.");
        }
        if (props.mail() != null && props.mail().devLogLinks()) {
            errors.add("MAIL_DEV_LOG_LINKS debe ser false en producción.");
        }
        if (env.getProperty("springdoc.api-docs.enabled", Boolean.class, true)) {
            errors.add("SWAGGER_ENABLED debe ser false en producción.");
        }
        if (props.security() == null || props.security().corsAllowedOrigins() == null
                || props.security().corsAllowedOrigins().isEmpty()) {
            errors.add("CORS_ALLOWED_ORIGINS debe incluir al menos el origen público del frontend.");
        } else if (props.security().corsAllowedOrigins().stream().anyMatch(this::unsafeOrigin)) {
            errors.add("CORS_ALLOWED_ORIGINS no puede incluir '*', orígenes HTTP ni localhost de desarrollo; "
                    + "para la app nativa use exactamente https://localhost y capacitor://localhost.");
        }

        String jwtSecret = props.jwt() == null ? null : props.jwt().secret();
        require(errors, jwtSecret != null && jwtSecret.getBytes(StandardCharsets.UTF_8).length >= 48,
                "JWT_SECRET debe tener al menos 48 bytes en producción.");
        require(errors, jwtSecret != null && !jwtSecret.startsWith("dev-only"),
                "JWT_SECRET no puede ser el secreto de desarrollo.");

        String dbPassword = env.getProperty("spring.datasource.password");
        require(errors, dbPassword != null && dbPassword.length() >= 16
                        && !dbPassword.equalsIgnoreCase("vecindad")
                        && !dbPassword.toLowerCase(Locale.ROOT).contains("change-me"),
                "DATABASE_PASSWORD debe tener al menos 16 caracteres y no ser un valor de ejemplo.");
        String redisPassword = env.getProperty("spring.data.redis.password");
        require(errors, redisPassword != null && redisPassword.length() >= 16
                        && !redisPassword.toLowerCase(Locale.ROOT).contains("change-me"),
                "REDIS_PASSWORD debe tener al menos 16 caracteres y no ser un valor de ejemplo.");

        String smtpHost = env.getProperty("spring.mail.host");
        String mailFrom = props.mail() == null ? null : props.mail().from();
        require(errors, StringUtils.hasText(smtpHost), "SMTP_HOST es obligatorio para enviar invitaciones y recuperación de contraseña.");
        require(errors, StringUtils.hasText(mailFrom) && mailFrom.contains("@"), "MAIL_FROM debe ser una dirección de correo válida.");

        if (props.wompi() != null && props.wompi().enabled()) {
            require(errors, StringUtils.hasText(props.wompi().publicKey())
                            && !props.wompi().publicKey().contains("_test_")
                            && !props.wompi().publicKey().contains("demo"),
                    "WOMPI_PUBLIC_KEY debe ser una llave de producción.");
            require(errors, StringUtils.hasText(props.wompi().privateKey())
                            && !props.wompi().privateKey().contains("_test_")
                            && !props.wompi().privateKey().contains("demo"),
                    "WOMPI_PRIVATE_KEY debe ser una llave de producción.");
            require(errors, StringUtils.hasText(props.wompi().eventsSecret())
                            && !props.wompi().eventsSecret().toLowerCase(Locale.ROOT).contains("test"),
                    "WOMPI_EVENTS_SECRET debe ser el secreto de producción.");
            require(errors, StringUtils.hasText(props.wompi().integritySecret())
                            && !props.wompi().integritySecret().toLowerCase(Locale.ROOT).contains("test"),
                    "WOMPI_INTEGRITY_SECRET debe ser el secreto de producción.");
            require(errors, props.wompi().apiUrl() != null && props.wompi().apiUrl().startsWith("https://")
                            && !props.wompi().apiUrl().contains("sandbox"),
                    "WOMPI_API_URL debe apuntar al endpoint HTTPS de producción.");
        }

        String storageDir = props.storage() == null ? null : props.storage().localDir();
        require(errors, StringUtils.hasText(storageDir)
                        && Path.of(storageDir).isAbsolute()
                        && !storageDir.startsWith("/tmp"),
                "STORAGE_LOCAL_DIR debe ser una ruta absoluta persistente (no /tmp).");

        if (!errors.isEmpty()) {
            throw new IllegalStateException("Configuración de producción inválida:\n - "
                    + String.join("\n - ", errors));
        }
    }

    private void require(List<String> errors, boolean condition, String message) {
        if (!condition) {
            errors.add(message);
        }
    }

    private boolean isHttps(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        try {
            URI uri = URI.create(value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                return false;
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            return !host.equals("localhost") && !host.equals("127.0.0.1") && !host.endsWith(".local");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean unsafeOrigin(String origin) {
        if (!StringUtils.hasText(origin)) {
            return true;
        }
        String value = origin.trim().toLowerCase(Locale.ROOT);
        if (value.equals("https://localhost") || value.equals("capacitor://localhost")
                || value.equals("ionic://localhost")) {
            return false;
        }
        return value.equals("*") || value.contains("localhost") || value.contains("127.0.0.1")
                || value.startsWith("http://");
    }
}
