package com.codevam.vecindad.tenancy.adapter.out;

import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.persistence.Jdbc;
import com.codevam.vecindad.tenancy.application.port.out.TenantPort;
import com.codevam.vecindad.tenancy.domain.Company;
import com.codevam.vecindad.tenancy.domain.Tenant;
import com.codevam.vecindad.tenancy.domain.TenantStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

@Repository
public class JdbcTenantAdapter implements TenantPort {
    private static final String TENANT_COLS = "id, company_id, name, nit, slug, schema_name, address, city, department, "
            + "phone, email, currency, timezone, locale, status, created_at";

    private static final RowMapper<Tenant> TENANT = (rs, i) -> new Tenant(
            Jdbc.uuid(rs, "id"), Jdbc.uuid(rs, "company_id"), rs.getString("name"), rs.getString("nit"),
            rs.getString("slug"), rs.getString("schema_name"), rs.getString("address"), rs.getString("city"),
            rs.getString("department"), rs.getString("phone"), rs.getString("email"), rs.getString("currency"),
            rs.getString("timezone"), rs.getString("locale"), TenantStatus.valueOf(rs.getString("status")),
            Jdbc.instant(rs, "created_at"));

    private static final RowMapper<Company> COMPANY = (rs, i) -> new Company(
            Jdbc.uuid(rs, "id"), rs.getString("name"), rs.getString("nit"), rs.getString("status"),
            Jdbc.instant(rs, "created_at"));

    private final JdbcTemplate jdbc;

    public JdbcTenantAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Company insertCompany(String name, String nit) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO public.administrator_companies (id, name, nit) VALUES (?, ?, ?)", id, name, nit);
        return findCompany(id).orElseThrow();
    }

    @Override
    public List<Company> listCompanies() {
        return jdbc.query("SELECT id, name, nit, status, created_at FROM public.administrator_companies ORDER BY name", COMPANY);
    }

    @Override
    public Optional<Company> findCompany(UUID id) {
        return jdbc.query("SELECT id, name, nit, status, created_at FROM public.administrator_companies WHERE id = ?",
                COMPANY, id).stream().findFirst();
    }

    @Override
    public Tenant insertTenant(Tenant t) {
        jdbc.update("""
                INSERT INTO public.tenants (id, company_id, name, nit, slug, schema_name, address, city, department,
                  phone, email, currency, timezone, locale, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, t.id(), t.companyId(), t.name(), t.nit(), t.slug(), t.schemaName(), t.address(), t.city(),
                t.department(), t.phone(), t.email(), t.currency(), t.timezone(), t.locale(), t.status().name());
        return findById(t.id()).orElseThrow();
    }

    @Override
    public void updateStatus(UUID id, TenantStatus status) {
        jdbc.update("UPDATE public.tenants SET status = ?, updated_at = now() WHERE id = ?", status.name(), id);
    }

    @Override
    public Optional<Tenant> findById(UUID id) {
        return jdbc.query("SELECT " + TENANT_COLS + " FROM public.tenants WHERE id = ?", TENANT, id).stream().findFirst();
    }

    @Override
    public boolean slugExists(String slug) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM public.tenants WHERE slug = ?", Integer.class, slug);
        return n != null && n > 0;
    }

    @Override
    public PageResult<Tenant> list(int page, int size) {
        Long total = jdbc.queryForObject("SELECT count(*) FROM public.tenants", Long.class);
        List<Tenant> rows = jdbc.query("SELECT " + TENANT_COLS + " FROM public.tenants ORDER BY name LIMIT ? OFFSET ?",
                TENANT, size, page * size);
        return PageResult.of(rows, page, size, total == null ? 0 : total);
    }

    @Override
    public List<String> schemasWithStatus(Collection<TenantStatus> statuses) {
        if (statuses.isEmpty()) return List.of();
        String in = statuses.stream().map(s -> "?").collect(Collectors.joining(","));
        return jdbc.queryForList("SELECT schema_name FROM public.tenants WHERE status IN (" + in + ") ORDER BY schema_name",
                String.class, statuses.stream().map(Enum::name).toArray());
    }

    @Override
    public void insertDefaultFeatures(UUID tenantId, Map<String, Boolean> features) {
        features.forEach((feature, enabled) -> jdbc.update(
                "INSERT INTO public.tenant_features (tenant_id, feature, enabled) VALUES (?, ?, ?) "
                        + "ON CONFLICT (tenant_id, feature) DO NOTHING", tenantId, feature, enabled));
    }

    @Override
    public Map<String, Boolean> features(UUID tenantId) {
        Map<String, Boolean> out = new LinkedHashMap<>();
        jdbc.query("SELECT feature, enabled FROM public.tenant_features WHERE tenant_id = ? ORDER BY feature",
                rs -> { out.put(rs.getString("feature"), rs.getBoolean("enabled")); }, tenantId);
        return out;
    }
}
