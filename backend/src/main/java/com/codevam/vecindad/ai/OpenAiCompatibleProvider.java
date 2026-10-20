package com.codevam.vecindad.ai;

import com.codevam.vecindad.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Adaptador para cualquier API compatible con OpenAI Chat Completions
 * (OpenAI, Azure OpenAI compatible, Groq, OpenRouter, modelos locales, etc.).
 */
class OpenAiCompatibleProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleProvider.class);

    private final AiProperties props;
    private final ObjectMapper mapper;
    private final RestClient client;

    OpenAiCompatibleProvider(AiProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(Math.max(5, props.timeoutSeconds())));
        String baseUrl = props.openaiBaseUrl() == null || props.openaiBaseUrl().isBlank()
                ? "https://api.openai.com/v1" : props.openaiBaseUrl();
        this.client = RestClient.builder()
                .baseUrl(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl)
                .requestFactory(factory)
                .build();
    }

    @Override
    public String name() {
        return "openai";
    }

    @Override
    public boolean isConfigured() {
        return props.enabled() && notBlank(props.openaiApiKey()) && notBlank(props.openaiModel());
    }

    @Override
    public String answer(String systemPrompt, String userPrompt) {
        Map<String, Object> payload = Map.of(
                "model", props.openaiModel(),
                "temperature", 0.2,
                "max_tokens", 700,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );
        try {
            byte[] bytes = client.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + props.openaiApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(mapper.writeValueAsString(payload))
                    .exchange((request, response) -> {
                        byte[] body = response.getBody().readAllBytes();
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new IllegalStateException("El proveedor compatible con OpenAI respondió "
                                    + response.getStatusCode().value() + ": "
                                    + snippet(new String(body, StandardCharsets.UTF_8)));
                        }
                        return body;
                    });
            String response = bytes == null ? "{}" : new String(bytes, StandardCharsets.UTF_8);
            JsonNode root = mapper.readTree(response == null ? "{}" : response);
            String text = root.path("choices").path(0).path("message").path("content").asText("");
            if (text.isBlank()) {
                throw new IllegalStateException("El proveedor compatible con OpenAI no devolvió contenido.");
            }
            return text.trim();
        } catch (RuntimeException e) {
            log.warn("Proveedor compatible con OpenAI falló: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.warn("No se pudo preparar la solicitud compatible con OpenAI: {}", e.getMessage());
            throw new IllegalStateException("No fue posible consultar el proveedor de IA.", e);
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String snippet(String value) {
        if (value == null) return "";
        String clean = value.replaceAll("\\s+", " ").trim();
        return clean.length() <= 300 ? clean : clean.substring(0, 300);
    }
}
