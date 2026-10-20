package com.codevam.vecindad.shared.whatsapp;

import com.codevam.vecindad.config.WhatsAppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

@Component
public class WhatsAppCloudGateway implements WhatsAppGateway {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppCloudGateway.class);
    private static final int MAX_TEXT_LENGTH = 4096;

    private final WhatsAppProperties props;
    private final ObjectMapper mapper;
    private final RestClient client;

    public WhatsAppCloudGateway(WhatsAppProperties props, ObjectMapper mapper, RestClient.Builder builder) {
        this.props = props;
        this.mapper = mapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(20));
        String baseUrl = props.baseUrl() == null || props.baseUrl().isBlank()
                ? "https://graph.facebook.com" : props.baseUrl();
        this.client = builder.clone()
                .baseUrl(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl)
                .requestFactory(factory)
                .build();
    }

    @Override
    public boolean isConfigured() {
        return props.isConfigured();
    }

    @Override
    public void sendText(String phone, String text) {
        if (!isConfigured()) {
            throw new IllegalStateException("WhatsApp Cloud API no está configurado.");
        }
        String to = digits(phone);
        if (to.isBlank()) {
            throw new IllegalArgumentException("El destinatario de WhatsApp no tiene un número válido.");
        }
        String body = text == null ? "" : text.trim();
        if (body.length() > MAX_TEXT_LENGTH) {
            body = body.substring(0, MAX_TEXT_LENGTH);
        }
        Map<String, Object> payload = Map.of(
                "messaging_product", "whatsapp",
                "recipient_type", "individual",
                "to", to,
                "type", "text",
                "text", Map.of("preview_url", false, "body", body)
        );
        try {
            String response = client.post()
                    .uri("/{version}/{phoneNumberId}/messages", props.apiVersion(), props.phoneNumberId())
                    .header("Authorization", "Bearer " + props.accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(mapper.writeValueAsString(payload))
                    .retrieve()
                    .body(String.class);
            log.info("WhatsApp enviado a {} (respuesta {} bytes)", mask(to), response == null ? 0 : response.length());
        } catch (RuntimeException e) {
            log.error("No se pudo enviar WhatsApp a {}: {}", mask(to), e.getMessage());
            throw new IllegalStateException("No fue posible enviar el mensaje de WhatsApp.", e);
        } catch (Exception e) {
            log.error("No se pudo serializar el mensaje de WhatsApp para {}: {}", mask(to), e.getMessage());
            throw new IllegalStateException("No fue posible preparar el mensaje de WhatsApp.", e);
        }
    }

    private static String digits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private static String mask(String phone) {
        if (phone.length() <= 4) return "****";
        return "****" + phone.substring(phone.length() - 4);
    }
}
