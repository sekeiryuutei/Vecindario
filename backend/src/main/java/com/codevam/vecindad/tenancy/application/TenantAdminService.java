package com.codevam.vecindad.tenancy.application;

import com.codevam.vecindad.audit.application.AuditService;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.model.Paging;
import com.codevam.vecindad.shared.tenancy.SchemaNames;
import com.codevam.vecindad.tenancy.application.port.out.TenantPort;
import com.codevam.vecindad.tenancy.application.port.out.TenantSchemaPort;
import com.codevam.vecindad.tenancy.domain.Company;
import com.codevam.vecindad.tenancy.domain.Tenant;
import com.codevam.vecindad.tenancy.domain.TenantStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Nota: createTenant NO es transaccional a propósito. La fila del tenant se confirma primero (PROVISIONING),
 * luego se crea/migra el schema y finalmente se marca ACTIVE; si algo falla queda en FAILED y es reintentable.
 */
@Service
public class TenantAdminService {
    private static final Logger log = LoggerFactory.getLogger(TenantAdminService.class);

    public record NewTenant(UUID companyId, String name, String nit, String slug, String address, String city,
                            String department, String phone, String email) {}

    private final TenantPort tenants;
    private final TenantSchemaPort schemas;
    private final AuditService audit;

    public TenantAdminService(TenantPort tenants, TenantSchemaPort schemas, AuditService audit) {
        this.tenants = tenants;
        this.schemas = schemas;
        this.audit = audit;
    }

    public Company createCompany(String name, String nit) {
        Company c = tenants.insertCompany(name.trim(), nit == null || nit.isBlank() ? null : nit.trim());
        audit.log(null, "COMPANY_CREATED", "COMPANY", c.id().toString(), true, Map.of("name", c.name()));
        return c;
    }

    public List<Company> listCompanies() {
        return tenants.listCompanies();
    }

    public Tenant createTenant(NewTenant cmd) {
        if (!SchemaNames.isValidSlug(cmd.slug())) {
            throw ApiException.badRequest("INVALID_SLUG",
                    "El identificador debe tener 3 a 40 caracteres: minúsculas, números o guion bajo, iniciando con letra.");
        }
        tenants.findCompany(cmd.companyId()).orElseThrow(() -> ApiException.notFound("La empresa administradora no existe."));
        if (tenants.slugExists(cmd.slug())) {
            throw ApiException.conflict("SLUG_ALREADY_EXISTS", "Ya existe una copropiedad con ese identificador.");
        }
        Tenant draft = new Tenant(UUID.randomUUID(), cmd.companyId(), cmd.name().trim(), cmd.nit(), cmd.slug(),
                SchemaNames.fromSlug(cmd.slug()), cmd.address(), cmd.city(), cmd.department(), cmd.phone(), cmd.email(),
                "COP", "America/Bogota", "es-CO", TenantStatus.PROVISIONING, null);
        Tenant saved = tenants.insertTenant(draft);
        try {
            schemas.migrate(saved.schemaName());
            tenants.insertDefaultFeatures(saved.id(), defaultFeatures());
            tenants.updateStatus(saved.id(), TenantStatus.ACTIVE);
        } catch (RuntimeException e) {
            log.error("Falló el aprovisionamiento del tenant {}", saved.schemaName(), e);
            tenants.updateStatus(saved.id(), TenantStatus.FAILED);
            audit.log(saved.id(), "TENANT_PROVISIONING_FAILED", "TENANT", saved.id().toString(), false, Map.of());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "TENANT_PROVISIONING_FAILED",
                    "No se pudo crear la copropiedad. Revisa los logs del backend.");
        }
        audit.log(saved.id(), "TENANT_CREATED", "TENANT", saved.id().toString(), true,
                Map.of("slug", saved.slug(), "companyId", saved.companyId().toString()));
        return tenants.findById(saved.id()).orElseThrow();
    }

    public PageResult<Tenant> listTenants(int page, int size) {
        return tenants.list(Paging.page(page), Paging.size(size));
    }

    public Tenant getTenant(UUID id) {
        return tenants.findById(id).orElseThrow(() -> ApiException.notFound("La copropiedad no existe."));
    }

    public Tenant setStatus(UUID id, TenantStatus status) {
        if (status != TenantStatus.ACTIVE && status != TenantStatus.SUSPENDED) {
            throw ApiException.badRequest("INVALID_STATUS", "Solo se puede activar o suspender una copropiedad.");
        }
        Tenant t = getTenant(id);
        if (t.status() == TenantStatus.PROVISIONING || t.status() == TenantStatus.FAILED) {
            throw ApiException.conflict("TENANT_NOT_READY", "La copropiedad no terminó de aprovisionarse.");
        }
        tenants.updateStatus(id, status);
        audit.log(id, status == TenantStatus.ACTIVE ? "TENANT_ACTIVATED" : "TENANT_SUSPENDED", "TENANT", id.toString(),
                true, Map.of());
        return getTenant(id);
    }

    public Map<String, Boolean> features(UUID tenantId) {
        return tenants.features(tenantId);
    }

    private static Map<String, Boolean> defaultFeatures() {
        Map<String, Boolean> f = new LinkedHashMap<>();
        f.put("WHATSAPP", false);
        f.put("CAMERAS", false);
        f.put("LPR", false);
        f.put("ONLINE_PAYMENTS", false);
        f.put("ASSEMBLIES", true);
        return f;
    }
}
