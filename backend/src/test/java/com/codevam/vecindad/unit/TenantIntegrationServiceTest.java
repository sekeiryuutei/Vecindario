package com.codevam.vecindad.unit;

import com.codevam.vecindad.config.AiProperties;
import com.codevam.vecindad.config.AppProperties;
import com.codevam.vecindad.integrations.SecretCipher;
import com.codevam.vecindad.integrations.TenantIntegrationService;
import com.codevam.vecindad.shared.error.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantIntegrationServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("6ee65acc-0970-4e9e-a0b6-f53cbed4fe45");
    private static final UUID ACTOR_ID = UUID.fromString("a2f1c0d4-1111-2222-3333-444455556666");

    private FakeJdbc jdbc;
    private SecretCipher cipher;
    private TenantIntegrationService service;

    @BeforeEach
    void setUp() {
        jdbc = new FakeJdbc();
        cipher = new SecretCipher("clave-de-prueba-con-32-caracteres-minimo", "jwt");
        service = new TenantIntegrationService(jdbc, new ObjectMapper(), cipher, props(), aiProps());
    }

    @Test
    void heredaLaConfiguracionGlobalCuandoNoHayPropia() {
        TenantIntegrationService.IntegrationsView view = service.view(TENANT_ID);

        assertTrue(view.wompi().inherited());
        assertTrue(view.wompi().configured());
        assertEquals("pub_test_global", view.wompi().publicKey());
        assertTrue(view.ai().inherited());
        assertEquals("gemini", view.ai().provider());
    }

    @Test
    void guardaLasCredencialesPropiasYLasDevuelveEnmascaradas() {
        service.saveWompi(TENANT_ID, ACTOR_ID, new TenantIntegrationService.WompiUpdate(
                "pub_test_tenant", "prv_test_tenant", "integrity_tenant_123456", "events_tenant_1234567"));

        TenantIntegrationService.IntegrationsView view = service.view(TENANT_ID);

        assertTrue(view.wompi().configured());
        assertFalse(view.wompi().inherited());
        assertEquals("pub_test_tenant", view.wompi().publicKey());
    }

    @Test
    void cifraLasCredencialesEnLaBaseDeDatos() {
        service.saveWompi(TENANT_ID, ACTOR_ID, new TenantIntegrationService.WompiUpdate(
                "pub_test_tenant", "prv_test_tenant", "integrity_tenant_123456", "events_tenant_1234567"));

        String almacenado = jdbc.rows.get(TENANT_ID + "|WOMPI");

        assertNotEquals("pub_test_tenant", almacenado);
        assertFalse(almacenado.contains("prv_test_tenant"));
        assertTrue(cipher.decrypt(almacenado).contains("prv_test_tenant"));
    }

    @Test
    void laVistaNuncaExponeLosSecretos() {
        service.saveWompi(TENANT_ID, ACTOR_ID, new TenantIntegrationService.WompiUpdate(
                "pub_test_tenant", "prv_test_tenant", "integrity_tenant_123456", "events_tenant_1234567"));

        String vista = service.view(TENANT_ID).toString();

        assertFalse(vista.contains("prv_test_tenant"));
        assertFalse(vista.contains("integrity_tenant_123456"));
        assertFalse(vista.contains("events_tenant_1234567"));
    }

    @Test
    void rechazaSecretosRepetidos() {
        ApiException error = assertThrows(ApiException.class, () -> service.saveWompi(TENANT_ID, ACTOR_ID,
                new TenantIntegrationService.WompiUpdate(
                        "pub_test_tenant", "prv_test_tenant", "mismo_secreto_123456", "mismo_secreto_123456")));

        assertEquals("REUSED_WOMPI_SECRET", error.getCode());
    }

    @Test
    void rechazaLlavePublicaInvalida() {
        ApiException error = assertThrows(ApiException.class, () -> service.saveWompi(TENANT_ID, ACTOR_ID,
                new TenantIntegrationService.WompiUpdate(
                        "llave_invalida", "prv_test_tenant", "integrity_tenant_123456", "events_tenant_1234567")));

        assertEquals("INVALID_WOMPI_PUBLIC_KEY", error.getCode());
    }

    @Test
    void exigeTodosLosSecretosEnLaConfiguracionInicial() {
        ApiException error = assertThrows(ApiException.class, () -> service.saveWompi(TENANT_ID, ACTOR_ID,
                new TenantIntegrationService.WompiUpdate("pub_test_tenant", null, null, null)));

        assertEquals("INVALID_WOMPI_PRIVATE_KEY", error.getCode());
    }

    @Test
    void conservaLosSecretosAlRotarSoloLaLlavePublica() {
        service.saveWompi(TENANT_ID, ACTOR_ID, new TenantIntegrationService.WompiUpdate(
                "pub_test_tenant", "prv_test_tenant", "integrity_tenant_123456", "events_tenant_1234567"));
        service.saveWompi(TENANT_ID, ACTOR_ID, new TenantIntegrationService.WompiUpdate(
                "pub_test_nueva", null, null, null));

        TenantIntegrationService.WompiConfig config = service.wompi(TENANT_ID);

        assertEquals("pub_test_nueva", config.publicKey());
        assertEquals("prv_test_tenant", config.privateKey());
        assertEquals("integrity_tenant_123456", config.integritySecret());
        assertEquals("events_tenant_1234567", config.eventsSecret());
    }

    @Test
    void laConfiguracionPropiaSustituyeALaGlobal() {
        service.saveWompi(TENANT_ID, ACTOR_ID, new TenantIntegrationService.WompiUpdate(
                "pub_test_tenant", "prv_test_tenant", "integrity_tenant_123456", "events_tenant_1234567"));

        TenantIntegrationService.WompiConfig config = service.wompi(TENANT_ID);

        assertEquals("pub_test_tenant", config.publicKey());
        assertEquals("prv_test_tenant", config.privateKey());
        assertTrue(config.enabled());
    }

    @Test
    void otraCopropiedadSigueUsandoLaConfiguracionGlobal() {
        service.saveWompi(TENANT_ID, ACTOR_ID, new TenantIntegrationService.WompiUpdate(
                "pub_test_tenant", "prv_test_tenant", "integrity_tenant_123456", "events_tenant_1234567"));

        TenantIntegrationService.WompiConfig otra = service.wompi(UUID.randomUUID());

        assertEquals("pub_test_global", otra.publicKey());
    }

    @Test
    void eliminarLaConfiguracionPropiaVuelveAlValorGlobal() {
        service.saveWompi(TENANT_ID, ACTOR_ID, new TenantIntegrationService.WompiUpdate(
                "pub_test_tenant", "prv_test_tenant", "integrity_tenant_123456", "events_tenant_1234567"));

        service.clear(TENANT_ID, "WOMPI");

        assertTrue(service.view(TENANT_ID).wompi().inherited());
        assertEquals("pub_test_global", service.wompi(TENANT_ID).publicKey());
    }

    @Test
    void rechazaIntegracionesDesconocidas() {
        ApiException error = assertThrows(ApiException.class, () -> service.clear(TENANT_ID, "PAYPAL"));

        assertEquals("RESOURCE_NOT_FOUND", error.getCode());
    }

    @Test
    void rechazaProveedorDeIaDesconocido() {
        ApiException error = assertThrows(ApiException.class, () -> service.saveAi(TENANT_ID, ACTOR_ID,
                new TenantIntegrationService.AiUpdate("deepseek", "clave", "modelo")));

        assertEquals("INVALID_AI_PROVIDER", error.getCode());
    }

    @Test
    void exigeClaveYModeloParaElProveedorElegido() {
        ApiException error = assertThrows(ApiException.class, () -> service.saveAi(TENANT_ID, ACTOR_ID,
                new TenantIntegrationService.AiUpdate("gemini", null, null)));

        assertEquals("INCOMPLETE_AI_CONFIG", error.getCode());
    }

    @Test
    void cambiarDeProveedorExigeLaNuevaClaveYElNuevoModelo() {
        service.saveAi(TENANT_ID, ACTOR_ID, new TenantIntegrationService.AiUpdate("gemini", "clave-gemini", "modelo-gemini"));

        ApiException error = assertThrows(ApiException.class, () -> service.saveAi(TENANT_ID, ACTOR_ID,
                new TenantIntegrationService.AiUpdate("openai", null, null)));

        assertEquals("INCOMPLETE_AI_CONFIG", error.getCode());
    }

    @Test
    void laConfiguracionPropiaDeIaSeUsaAlResponder() {
        service.saveAi(TENANT_ID, ACTOR_ID, new TenantIntegrationService.AiUpdate("gemini", "clave-propia", "modelo-propio"));

        TenantIntegrationService.AiConfig config = service.ai(TENANT_ID);

        assertEquals("gemini", config.provider());
        assertEquals("clave-propia", config.geminiApiKey());
        assertEquals("modelo-propio", config.geminiModel());
    }

    @Test
    void sinConfiguracionPropiaDeIaSeUsanLosValoresGlobales() {
        TenantIntegrationService.AiConfig config = service.ai(TENANT_ID);

        assertEquals("gemini", config.provider());
        assertEquals("gemini-global-key", config.geminiApiKey());
        assertEquals("gemini-3.7-flash", config.geminiModel());
    }

    @Test
    void rechazaCopropiedadesInexistentes() {
        jdbc.tenantExists = false;

        ApiException error = assertThrows(ApiException.class, () -> service.view(TENANT_ID));

        assertEquals("RESOURCE_NOT_FOUND", error.getCode());
    }

    private static AppProperties props() {
        return new AppProperties(
                "Vecindad",
                "CodeVam",
                "development",
                "http://localhost:48124",
                new AppProperties.Mail(true, "no-reply@demo.local"),
                new AppProperties.Jwt("jwt-secret-de-prueba", "vecindad", Duration.ofMinutes(15), Duration.ofDays(7)),
                new AppProperties.Security(5, Duration.ofMinutes(15), Duration.ofHours(2), Duration.ofDays(2),
                        10, 10, List.of()),
                new AppProperties.Seed(true, "Demo#2026!"),
                new AppProperties.Bootstrap("superadmin@vecindad.local", "Demo#2026!", "Super Admin"),
                new AppProperties.Wompi(true, "pub_test_global", "prv_test_global",
                        "events_secret_global_1234", "integrity_secret_global_12",
                        "https://sandbox.wompi.co/v1", "http://localhost:48124/pagos/wompi/resultado"),
                new AppProperties.Storage("./data"),
                new AppProperties.Maintenance("0 0 3 * * *", 30));
    }

    private static AiProperties aiProps() {
        return new AiProperties(true, "gemini", "gemini-global-key", "gemini-3.7-flash",
                "https://generativelanguage.googleapis.com/v1beta", "openai-global-key", "gpt-4o-mini",
                "https://api.openai.com/v1", List.of("local"), 20);
    }

    /** Banco de datos en memoria con el comportamiento usado por el servicio de integraciones. */
    private static final class FakeJdbc extends JdbcTemplate {

        private final Map<String, String> rows = new LinkedHashMap<>();
        private boolean tenantExists = true;

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.contains("EXISTS")) {
                return requiredType.cast(Boolean.valueOf(tenantExists));
            }
            if (sql.contains("config_ciphertext")) {
                String value = rows.get(args[0] + "|" + args[1]);
                if (value == null) {
                    throw new EmptyResultDataAccessException(1);
                }
                return requiredType.cast(value);
            }
            return null;
        }

        @Override
        public int update(String sql, Object... args) {
            if (sql.contains("INSERT INTO public.tenant_integrations")) {
                rows.put(args[0] + "|" + args[1], (String) args[2]);
                return 1;
            }
            if (sql.contains("DELETE FROM public.tenant_integrations")) {
                rows.remove(args[0] + "|" + args[1]);
                return 1;
            }
            return 0;
        }
    }
}
