package com.codevam.vecindad.ai;

import com.codevam.vecindad.config.AiProperties;
import com.codevam.vecindad.integrations.TenantIntegrationService;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Selecciona el proveedor de IA según la copropiedad activa. Si la
 * copropiedad tiene credenciales propias se usan esas; en caso contrario se
 * conservan las credenciales globales del entorno. Así una llave no se
 * comparte entre copropiedades cuando el operador configura una propia.
 */
public class TenantAiGateway implements AiGateway {

    private final AiProperties global;
    private final TenantIntegrationService integrations;
    private final ObjectMapper mapper;

    public TenantAiGateway(AiProperties global, TenantIntegrationService integrations, ObjectMapper mapper) {
        this.global = global;
        this.integrations = integrations;
        this.mapper = mapper;
    }

    @Override
    public boolean isConfigured() {
        return routing().isConfigured();
    }

    @Override
    public String providerName() {
        return routing().providerName();
    }

    @Override
    public String answer(String systemPrompt, String userPrompt) {
        return routing().answer(systemPrompt, userPrompt);
    }

    private RoutingAiGateway routing() {
        TenantIntegrationService.AiConfig config = TenantContext.current()
                .map(tenant -> integrations.ai(tenant.id()))
                .orElseGet(integrations::defaultAi);
        AiProperties effective = new AiProperties(
                config.enabled(),
                config.provider(),
                config.geminiApiKey(),
                config.geminiModel(),
                config.geminiBaseUrl(),
                config.openaiApiKey(),
                config.openaiModel(),
                config.openaiBaseUrl(),
                config.fallbackProviders(),
                config.timeoutSeconds() > 0 ? config.timeoutSeconds() : global.timeoutSeconds()
        );
        return new RoutingAiGateway(effective,
                new GeminiAiProvider(effective, mapper),
                new OpenAiCompatibleProvider(effective, mapper));
    }
}
