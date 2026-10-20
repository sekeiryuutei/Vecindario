package com.codevam.vecindad.ai;

import com.codevam.vecindad.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Enruta la consulta al proveedor principal y luego a los de respaldo. Si todos
 * fallan, responde el proveedor local sin exponer detalles internos al usuario.
 */
public class RoutingAiGateway implements AiGateway {

    private static final Logger log = LoggerFactory.getLogger(RoutingAiGateway.class);

    private final AiProperties props;
    private final Map<String, AiProvider> providers;
    private final List<String> order;
    private final LocalAiProvider local;

    RoutingAiGateway(AiProperties props, GeminiAiProvider gemini, OpenAiCompatibleProvider openai) {
        this.props = props;
        this.providers = new LinkedHashMap<>();
        this.providers.put("gemini", gemini);
        this.providers.put("openai", openai);
        this.local = new LocalAiProvider();
        this.providers.put("local", local);
        this.order = resolveOrder(props);
    }

    @Override
    public boolean isConfigured() {
        return order.stream().map(providers::get).filter(p -> p != null && !"local".equals(p.name()))
                .anyMatch(AiProvider::isConfigured);
    }

    @Override
    public String providerName() {
        return order.stream().map(providers::get).filter(p -> p != null && p.isConfigured())
                .map(AiProvider::name).findFirst().orElse("local");
    }

    @Override
    public String answer(String systemPrompt, String userPrompt) {
        if (!props.enabled()) {
            return local.answer(systemPrompt, userPrompt);
        }
        for (String name : order) {
            AiProvider provider = providers.get(name);
            if (provider == null || !provider.isConfigured()) {
                continue;
            }
            try {
                String answer = provider.answer(systemPrompt, userPrompt);
                if (answer != null && !answer.isBlank()) {
                    return answer.trim();
                }
            } catch (RuntimeException e) {
                log.warn("El proveedor de IA {} falló; se intenta el siguiente. {}", provider.name(), e.getMessage());
            }
        }
        return local.answer(systemPrompt, userPrompt);
    }

    private static List<String> resolveOrder(AiProperties props) {
        List<String> result = new ArrayList<>();
        add(result, props.provider());
        if (props.fallbackProviders() != null) {
            props.fallbackProviders().forEach(value -> add(result, value));
        }
        add(result, "local");
        return result;
    }

    private static void add(List<String> result, String value) {
        if (value == null || value.isBlank()) return;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!result.contains(normalized)) {
            result.add(normalized);
        }
    }
}
