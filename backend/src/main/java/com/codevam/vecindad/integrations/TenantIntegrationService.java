package com.codevam.vecindad.integrations;

import com.codevam.vecindad.config.AiProperties;
import com.codevam.vecindad.config.AppProperties;
import com.codevam.vecindad.shared.error.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Resuelve las credenciales de Wompi y del proveedor de IA de cada
 * copropiedad. Si no existe una configuración propia se usan los valores
 * globales del entorno, de modo que las instalaciones existentes siguen
 * funcionando sin cambios.
 */
@Service
public class TenantIntegrationService {

    public record WompiConfig(boolean enabled, String publicKey, String privateKey,
                              String eventsSecret, String integritySecret, String apiUrl) {}

    public record AiConfig(boolean enabled, String provider, String geminiApiKey, String geminiModel,
                           String geminiBaseUrl, String openaiApiKey, String openaiModel,
                           String openaiBaseUrl, List<String> fallbackProviders, int timeoutSeconds) {}

    public record WompiView(boolean configured, boolean inherited, String publicKey, String apiUrl) {}

    public record AiView(boolean configured, boolean inherited, String provider, String model) {}

    public record IntegrationsView(WompiView wompi, AiView ai) {}

    public record WompiUpdate(String publicKey, String privateKey, String integritySecret, String eventsSecret) {}

    public record AiUpdate(String provider, String apiKey, String model) {}

    private record WompiStored(String publicKey, String privateKey, String integritySecret,
                               String eventsSecret, String apiUrl) {}

    private record AiStored(String provider, String apiKey, String model) {}

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final SecretCipher cipher;
    private final AppProperties props;
    private final AiProperties aiProps;

    public TenantIntegrationService(JdbcTemplate jdbc, ObjectMapper mapper, SecretCipher cipher,
                                    AppProperties props, AiProperties aiProps) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.cipher = cipher;
        this.props = props;
        this.aiProps = aiProps;
    }

    public WompiConfig wompi(UUID tenantId) {
        WompiStored stored = read(tenantId, "WOMPI", WompiStored.class).orElse(null);
        AppProperties.Wompi global = props.wompi();
        if (stored == null) {
            if (global == null) {
                return new WompiConfig(false, null, null, null, null, null);
            }
            return new WompiConfig(global.enabled(), global.publicKey(), global.privateKey(),
                    global.eventsSecret(), global.integritySecret(), global.apiUrl());
        }
        return new WompiConfig(
                global != null && global.enabled(),
                stored.publicKey(),
                stored.privateKey(),
                stored.eventsSecret(),
                stored.integritySecret(),
                StringUtils.hasText(stored.apiUrl()) ? stored.apiUrl()
                        : global == null ? null : global.apiUrl()
        );
    }

    public AiConfig defaultAi() {
        return fromGlobal(aiProps);
    }

    public AiConfig ai(UUID tenantId) {        AiStored stored = read(tenantId, "AI", AiStored.class).orElse(null);
        if (stored == null) {
            return fromGlobal(aiProps);
        }
        String provider = normalizeProvider(stored.provider());
        String key = stored.apiKey();
        String model = stored.model();
        return new AiConfig(
                aiProps != null && aiProps.enabled(),
                provider,
                "gemini".equals(provider) ? key : null,
                "gemini".equals(provider) && StringUtils.hasText(model) ? model : safe(aiProps == null ? null : aiProps.geminiModel()),
                safe(aiProps == null ? null : aiProps.geminiBaseUrl()),
                "openai".equals(provider) ? key : null,
                "openai".equals(provider) && StringUtils.hasText(model) ? model : safe(aiProps == null ? null : aiProps.openaiModel()),
                safe(aiProps == null ? null : aiProps.openaiBaseUrl()),
                aiProps == null || aiProps.fallbackProviders() == null ? List.of("local") : aiProps.fallbackProviders(),
                aiProps == null ? 20 : aiProps.timeoutSeconds()
        );
    }

    public IntegrationsView view(UUID tenantId) {
        requireTenant(tenantId);
        WompiStored wompi = read(tenantId, "WOMPI", WompiStored.class).orElse(null);
        AiStored ai = read(tenantId, "AI", AiStored.class).orElse(null);
        AppProperties.Wompi global = props.wompi();
        WompiView wompiView = wompi == null
                ? new WompiView(global != null && StringUtils.hasText(global.publicKey()), true,
                global == null ? null : global.publicKey(), global == null ? null : global.apiUrl())
                : new WompiView(true, false, wompi.publicKey(), wompi.apiUrl());
        AiView aiView = ai == null
                ? new AiView(aiProps != null && StringUtils.hasText(aiProps.provider()), true,
                aiProps == null ? "local" : aiProps.provider(), aiProps == null ? null : modelFor(aiProps.provider(), aiProps))
                : new AiView(true, false, ai.provider(), ai.model());
        return new IntegrationsView(wompiView, aiView);
    }

    public IntegrationsView saveWompi(UUID tenantId, UUID actor, WompiUpdate update) {
        requireTenant(tenantId);
        WompiStored current = read(tenantId, "WOMPI", WompiStored.class).orElse(null);
        WompiStored merged = new WompiStored(
                pick(update.publicKey(), current == null ? null : current.publicKey()),
                pick(update.privateKey(), current == null ? null : current.privateKey()),
                pick(update.integritySecret(), current == null ? null : current.integritySecret()),
                pick(update.eventsSecret(), current == null ? null : current.eventsSecret()),
                current == null ? null : current.apiUrl()
        );
        validateWompi(merged);
        write(tenantId, "WOMPI", merged, actor);
        return view(tenantId);
    }

    public IntegrationsView saveAi(UUID tenantId, UUID actor, AiUpdate update) {
        requireTenant(tenantId);
        AiStored current = read(tenantId, "AI", AiStored.class).orElse(null);
        String provider = normalizeProvider(pick(update.provider(), current == null ? null : current.provider()));
        String apiKey = pick(update.apiKey(), current == null ? null : current.apiKey());
        String model = pick(update.model(), current == null ? null : current.model());
        boolean providerChanged = current != null
                && !normalizeProvider(current.provider()).equals(provider);
        if (!List.of("gemini", "openai", "local").contains(provider)) {
            throw ApiException.badRequest("INVALID_AI_PROVIDER",
                    "El proveedor de IA debe ser gemini, openai o local.");
        }
        if (providerChanged && (!StringUtils.hasText(update.apiKey()) || !StringUtils.hasText(update.model()))
                && !"local".equals(provider)) {
            throw ApiException.badRequest("INCOMPLETE_AI_CONFIG",
                    "Al cambiar de proveedor debes indicar la nueva clave y el nuevo modelo.");
        }
        if (!"local".equals(provider) && (!StringUtils.hasText(apiKey) || !StringUtils.hasText(model))) {
            throw ApiException.badRequest("INCOMPLETE_AI_CONFIG",
                    "El proveedor de IA seleccionado requiere una clave y un modelo.");
        }
        write(tenantId, "AI", new AiStored(provider, apiKey, model), actor);
        return view(tenantId);
    }

    public void clear(UUID tenantId, String provider) {
        requireTenant(tenantId);
        String normalized = provider == null ? "" : provider.trim().toUpperCase(Locale.ROOT);
        if (!List.of("WOMPI", "AI").contains(normalized)) {
            throw ApiException.notFound("La integración indicada no existe.");
        }
        jdbc.update("DELETE FROM public.tenant_integrations WHERE tenant_id = ? AND provider = ?",
                tenantId, normalized);
    }

    private void validateWompi(WompiStored value) {
        if (!StringUtils.hasText(value.publicKey()) || !value.publicKey().startsWith("pub_")) {
            throw ApiException.badRequest("INVALID_WOMPI_PUBLIC_KEY",
                    "La llave pública de Wompi debe iniciar con pub_.");
        }
        if (!StringUtils.hasText(value.privateKey()) || !value.privateKey().startsWith("prv_")) {
            throw ApiException.badRequest("INVALID_WOMPI_PRIVATE_KEY",
                    "La llave privada de Wompi debe iniciar con prv_.");
        }
        if (!validSecret(value.integritySecret())) {
            throw ApiException.badRequest("INVALID_WOMPI_INTEGRITY_SECRET",
                    "El secreto de integridad de Wompi no es válido.");
        }
        if (!validSecret(value.eventsSecret())) {
            throw ApiException.badRequest("INVALID_WOMPI_EVENTS_SECRET",
                    "El secreto de eventos de Wompi no es válido.");
        }
        if (value.eventsSecret().equals(value.integritySecret())) {
            throw ApiException.badRequest("REUSED_WOMPI_SECRET",
                    "Los secretos de integridad y de eventos deben ser diferentes.");
        }
        if (StringUtils.hasText(value.apiUrl()) && !validHttps(value.apiUrl())) {
            throw ApiException.badRequest("INVALID_WOMPI_API_URL",
                    "La URL de la API de Wompi debe usar HTTPS.");
        }
    }

    private boolean validSecret(String value) {
        return StringUtils.hasText(value) && value.trim().length() >= 16;
    }

    private boolean validHttps(String value) {
        try {
            URI uri = URI.create(value.trim());
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void requireTenant(UUID tenantId) {
        Boolean exists = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM public.tenants WHERE id = ?)",
                Boolean.class, tenantId);
        if (!Boolean.TRUE.equals(exists)) {
            throw ApiException.notFound("La copropiedad no existe.");
        }
    }

    private <T> Optional<T> read(UUID tenantId, String provider, Class<T> type) {
        try {
            String cipherText = jdbc.queryForObject("""
                    SELECT config_ciphertext
                      FROM public.tenant_integrations
                     WHERE tenant_id = ? AND provider = ?
                    """, String.class, tenantId, provider);
            return Optional.of(mapper.readValue(cipher.decrypt(cipherText), type));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        } catch (Exception e) {
            throw new IllegalStateException("No fue posible leer la integración " + provider + " de la copropiedad.", e);
        }
    }

    private void write(UUID tenantId, String provider, Object config, UUID actor) {
        try {
            String cipherText = cipher.encrypt(mapper.writeValueAsString(config));
            jdbc.update("""
                    INSERT INTO public.tenant_integrations
                        (tenant_id, provider, config_ciphertext, updated_by, updated_at)
                    VALUES (?, ?, ?, ?, now())
                    ON CONFLICT (tenant_id, provider) DO UPDATE
                       SET config_ciphertext = EXCLUDED.config_ciphertext,
                           updated_by = EXCLUDED.updated_by,
                           updated_at = now()
                    """, tenantId, provider, cipherText, actor);
        } catch (Exception e) {
            throw new IllegalStateException("No fue posible guardar la integración " + provider + ".", e);
        }
    }

    private AiConfig fromGlobal(AiProperties global) {
        if (global == null) {
            return new AiConfig(false, "local", null, null, null, null, null, null,
                    List.of("local"), 20);
        }
        return new AiConfig(global.enabled(), normalizeProvider(global.provider()), global.geminiApiKey(),
                global.geminiModel(), global.geminiBaseUrl(), global.openaiApiKey(), global.openaiModel(),
                global.openaiBaseUrl(), global.fallbackProviders(), global.timeoutSeconds());
    }

    private String modelFor(String provider, AiProperties properties) {
        return "openai".equalsIgnoreCase(provider) ? properties.openaiModel() : properties.geminiModel();
    }

    private String normalizeProvider(String provider) {
        return StringUtils.hasText(provider) ? provider.trim().toLowerCase(Locale.ROOT) : "local";
    }

    private String pick(String incoming, String current) {
        return StringUtils.hasText(incoming) ? incoming.trim() : current;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
