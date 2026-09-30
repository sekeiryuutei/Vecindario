package com.codevam.vecindad.tenancy.adapter.in.web;

import com.codevam.vecindad.identity.application.MemberService;
import com.codevam.vecindad.identity.domain.MemberView;
import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.security.CurrentUser;
import com.codevam.vecindad.tenancy.application.TenantAdminService;
import com.codevam.vecindad.tenancy.domain.Company;
import com.codevam.vecindad.tenancy.domain.Tenant;
import com.codevam.vecindad.tenancy.domain.TenantStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Área global. Toda la ruta /api/v1/platform/** exige el rol SUPER_ADMIN_PLATFORM (ver SecurityConfig). */
@RestController
@RequestMapping("/api/v1/platform")
@Tag(name = "Plataforma (super admin)")
public class PlatformController {

    public record CreateCompanyRequest(@NotBlank @Size(max = 200) String name, @Size(max = 30) String nit) {}
    public record CreateTenantRequest(@NotNull UUID companyId, @NotBlank @Size(max = 200) String name,
                                      @Size(max = 30) String nit, @NotBlank @Size(min = 3, max = 40) String slug,
                                      @Size(max = 250) String address, @Size(max = 100) String city,
                                      @Size(max = 100) String department, @Size(max = 40) String phone,
                                      @Email @Size(max = 254) String email) {}
    public record StatusRequest(@NotNull TenantStatus status) {}
    public record AddMemberRequest(@NotBlank @Email @Size(max = 254) String email,
                                   @NotBlank @Size(max = 200) String fullName, @NotBlank @Size(max = 40) String role) {}

    private final TenantAdminService tenants;
    private final MemberService members;

    public PlatformController(TenantAdminService tenants, MemberService members) {
        this.tenants = tenants;
        this.members = members;
    }

    @Operation(summary = "Crea una empresa administradora")
    @PostMapping("/companies")
    @ResponseStatus(HttpStatus.CREATED)
    public Company createCompany(@Valid @RequestBody CreateCompanyRequest req) {
        return tenants.createCompany(req.name(), req.nit());
    }

    @Operation(summary = "Lista empresas administradoras")
    @GetMapping("/companies")
    public List<Company> companies() {
        return tenants.listCompanies();
    }

    @Operation(summary = "Crea una copropiedad: registra el tenant, crea su schema y aplica sus migraciones")
    @PostMapping("/tenants")
    @ResponseStatus(HttpStatus.CREATED)
    public TenantController.TenantView createTenant(@Valid @RequestBody CreateTenantRequest r) {
        Tenant t = tenants.createTenant(new TenantAdminService.NewTenant(r.companyId(), r.name(), r.nit(), r.slug(),
                r.address(), r.city(), r.department(), r.phone(), r.email()));
        return TenantController.TenantView.of(t, tenants.features(t.id()));
    }

    @Operation(summary = "Lista copropiedades")
    @GetMapping("/tenants")
    public PageResult<TenantController.TenantView> listTenants(@RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        return tenants.listTenants(page, size).map(t -> TenantController.TenantView.of(t, tenants.features(t.id())));
    }

    @Operation(summary = "Activa o suspende una copropiedad")
    @PatchMapping("/tenants/{tenantId}/status")
    public TenantController.TenantView setStatus(@PathVariable UUID tenantId, @Valid @RequestBody StatusRequest req) {
        Tenant t = tenants.setStatus(tenantId, req.status());
        return TenantController.TenantView.of(t, tenants.features(t.id()));
    }

    @Operation(summary = "Agrega el primer administrador (u otro miembro) a una copropiedad")
    @PostMapping("/tenants/{tenantId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberView addMember(@PathVariable UUID tenantId, @Valid @RequestBody AddMemberRequest req) {
        return members.addMember(tenantId, CurrentUser.require(), req.email(), req.fullName(), req.role());
    }
}
