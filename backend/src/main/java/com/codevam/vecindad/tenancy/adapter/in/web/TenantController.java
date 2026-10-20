package com.codevam.vecindad.tenancy.adapter.in.web;

import com.codevam.vecindad.shared.security.CurrentUser;
import com.codevam.vecindad.tenancy.application.TenantAdminService;
import com.codevam.vecindad.tenancy.domain.Tenant;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants")
@Tag(name = "Copropiedad activa")
public class TenantController {

    public record TenantView(UUID id, String name, String nit, String slug, String address, String city,
                             String department, String phone, String email, String currency, String timezone,
                             String locale, String status, Map<String, Boolean> features) {
        public static TenantView of(Tenant t, Map<String, Boolean> features) {
            return new TenantView(t.id(), t.name(), t.nit(), t.slug(), t.address(), t.city(), t.department(),
                    t.phone(), t.email(), t.currency(), t.timezone(), t.locale(), t.status().name(), features);
        }
    }

    public record UpdateTenantRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 30) String nit,
            @Size(max = 250) String address,
            @Size(max = 100) String city,
            @Size(max = 100) String department,
            @Size(max = 40) String phone,
            @Email @Size(max = 254) String email
    ) {}

    public record TenantFeatureRequest(@NotNull Boolean enabled) {}

    private final TenantAdminService service;

    public TenantController(TenantAdminService service) {
        this.service = service;
    }

    @Operation(summary = "Datos de la copropiedad activa (la del token)")
    @GetMapping("/current")
    public TenantView current() {
        UUID id = CurrentUser.requireTenant().tenantId();
        return TenantView.of(service.getTenant(id), service.features(id));
    }

    @Operation(summary = "Actualiza los datos editables de la copropiedad activa")
    @PutMapping("/current")
    @PreAuthorize("hasAuthority('TENANT_SETTINGS_MANAGE')")
    public TenantView updateCurrent(@Valid @RequestBody UpdateTenantRequest r) {
        UUID id = CurrentUser.requireTenant().tenantId();
        Tenant t = service.updateDetails(id, new TenantAdminService.UpdateTenantCommand(
                r.name(), r.nit(), r.address(), r.city(), r.department(), r.phone(), r.email()));
        return TenantView.of(t, service.features(id));
    }

    @Operation(summary = "Activa o desactiva una funcionalidad autogestionable de la copropiedad")
    @PutMapping("/current/features/{feature}")
    @PreAuthorize("hasAuthority('TENANT_SETTINGS_MANAGE')")
    public Map<String, Boolean> setFeature(@PathVariable String feature, @Valid @RequestBody TenantFeatureRequest r) {
        return service.setSelfServiceFeature(CurrentUser.requireTenant().tenantId(), feature, r.enabled());
    }
}
