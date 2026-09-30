package com.codevam.vecindad.tenancy.adapter.in.web;

import com.codevam.vecindad.shared.security.CurrentUser;
import com.codevam.vecindad.tenancy.application.TenantAdminService;
import com.codevam.vecindad.tenancy.domain.Tenant;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
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
}
