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

class GeminiAiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiAiProvider.class);

    private final AiProperties props;
    private final ObjectMapper mapper;
    private final RestClient client;

    GeminiAiProvider(AiProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(Math.max(5, props.timeoutSeconds())));
        String baseUrl = props.geminiBaseUrl() == null || props.geminiBaseUrl().isBlank()
                ? "https://generativelanguage.googleapis.com" : props.geminiBaseUrl();
        this.client = RestClient.builder()
                .baseUrl(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl)
                .requestFactory(factory)
                .build();
    }

    @Override
    public String name() {
        return "gemini";
    }

    @Override
    public boolean isConfigured() {
        return props.enabled() && notBlank(props.geminiApiKey()) && notBlank(props.geminiModel());
    }

    @Override
    public String answer(String systemPrompt, String userPrompt) {
        Map<String, Object> payload = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", userPrompt)))),
                "generationConfig", Map.of("temperature", 0.2, "maxOutputTokens", 700)
        );
        try {
            byte[] bytes = client.post()
                    .uri("/v1beta/models/{model}:generateContent", props.geminiModel())
                    .header("x-goog-api-key", props.geminiApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(mapper.writeValueAsString(payload))
                    .exchange((request, response) -> {
                        byte[] body = response.getBody().readAllBytes();
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new IllegalStateException("Gemini respondió "
                                    + response.getStatusCode().value() + ": "
                                    + snippet(new String(body, StandardCharsets.UTF_8)));
                        }
                        return body;
                    });
            String response = bytes == null ? "{}" : new String(bytes, StandardCharsets.UTF_8);
            JsonNode root = mapper.readTree(response == null ? "{}" : response);
            StringBuilder text = new StringBuilder();
            for (JsonNode part : root.path("candidates").path(0).path("content").path("parts")) {
                String value = part.path("text").asText("");
                if (!value.isBlank()) {
                    if (!text.isEmpty()) text.append('\n');
                    text.append(value.trim());
                }
            }
            if (text.isEmpty()) {
                throw new IllegalStateException("Gemini no devolvió contenido.");
            }
            return text.toString();
        } catch (RuntimeException e) {
            log.warn("Proveedor Gemini falló: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.warn("No se pudo preparar la solicitud a Gemini: {}", e.getMessage());
            throw new IllegalStateException("No fue posible consultar Gemini.", e);
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
