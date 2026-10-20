package com.codevam.vecindad.unit;

import com.codevam.vecindad.ai.AiGateway;
import com.codevam.vecindad.config.WhatsAppProperties;
import com.codevam.vecindad.shared.tenancy.TenantJdbc;
import com.codevam.vecindad.shared.whatsapp.WhatsAppGateway;
import com.codevam.vecindad.tenancy.application.port.out.TenantPort;
import com.codevam.vecindad.whatsapp.application.WhatsAppInboundService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WhatsAppInboundServiceTest {

    private WhatsAppInboundService service(String secret) {
        WhatsAppProperties props = new WhatsAppProperties(true, "verify-token", secret, "v21.0",
                "token", "phone-id", "https://graph.facebook.com", true, false);
        return new WhatsAppInboundService(props, Mockito.mock(TenantPort.class), Mockito.mock(TenantJdbc.class),
                Mockito.mock(JdbcTemplate.class), Mockito.mock(WhatsAppGateway.class),
                Mockito.mock(AiGateway.class), new ObjectMapper());
    }

    @Test
    void acceptsValidMetaSignature() throws Exception {
        String payload = "{\"entry\":[]}";
        String signature = sign(payload, "app-secret");
        assertTrue(service("app-secret").validSignature(payload, signature));
    }

    @Test
    void rejectsTamperedSignature() throws Exception {
        String payload = "{\"entry\":[]}";
        String signature = sign(payload + " ", "app-secret");
        assertFalse(service("app-secret").validSignature(payload, signature));
    }

    @Test
    void rejectsMissingSecretOrHeader() {
        assertFalse(service("").validSignature("{}", "sha256=00"));
        assertFalse(service("app-secret").validSignature("{}", null));
        assertFalse(service("app-secret").validSignature("{}", "sha1=00"));
    }

    private static String sign(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
