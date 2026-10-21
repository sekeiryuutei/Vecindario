package com.codevam.vecindad.integrations;

import com.codevam.vecindad.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Administración de credenciales por copropiedad. Solo el superadministrador
 * de plataforma puede leer el estado enmascarado o rotar las llaves; los
 * secretos nunca se devuelven al navegador.
 */
@RestController
@RequestMapping("/api/v1/platform/tenants/{tenantId}/integrations")
@Tag(name = "Integraciones por copropiedad")
public class PlatformIntegrationController {

    public record WompiUpdateRequest(
            @Size(max = 200) String publicKey,
            @Size(max = 200) String privateKey,
            @Size(max = 300) String integritySecret,
            @Size(max = 300) String eventsSecret
    ) {}

    public record AiUpdateRequest(
            @Size(max = 30) String provider,
            @Size(max = 500) String apiKey,
            @Size(max = 120) String model
    ) {}

    private final TenantIntegrationService integrations;

    public PlatformIntegrationController(TenantIntegrationService integrations) {
        this.integrations = integrations;
    }

    @Operation(summary = "Consulta el estado enmascarado de las integraciones de una copropiedad")
    @GetMapping
    public TenantIntegrationService.IntegrationsView get(@PathVariable UUID tenantId) {
        return integrations.view(tenantId);
    }

    @Operation(summary = "Configura o rota las credenciales de Wompi de una copropiedad")
    @PutMapping("/wompi")
    public TenantIntegrationService.IntegrationsView saveWompi(@PathVariable UUID tenantId,
                                                               @Valid @RequestBody WompiUpdateRequest request) {
        return integrations.saveWompi(tenantId, CurrentUser.require().userId(),
                new TenantIntegrationService.WompiUpdate(request.publicKey(), request.privateKey(),
                        request.integritySecret(), request.eventsSecret()));
    }

    @Operation(summary = "Configura o rota el proveedor de IA de una copropiedad")
    @PutMapping("/ai")
    public TenantIntegrationService.IntegrationsView saveAi(@PathVariable UUID tenantId,
                                                            @Valid @RequestBody AiUpdateRequest request) {
        return integrations.saveAi(tenantId, CurrentUser.require().userId(),
                new TenantIntegrationService.AiUpdate(request.provider(), request.apiKey(), request.model()));
    }

    @Operation(summary = "Elimina la configuración propia y vuelve a los valores globales")
    @DeleteMapping("/{provider}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@PathVariable UUID tenantId, @PathVariable String provider) {
        integrations.clear(tenantId, provider);
    }
}
