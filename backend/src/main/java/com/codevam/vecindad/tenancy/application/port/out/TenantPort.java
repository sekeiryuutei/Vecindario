package com.codevam.vecindad.tenancy.application.port.out;

import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.tenancy.domain.Company;
import com.codevam.vecindad.tenancy.domain.Tenant;
import com.codevam.vecindad.tenancy.domain.TenantStatus;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface TenantPort {
    Company insertCompany(String name, String nit);
    List<Company> listCompanies();
    Optional<Company> findCompany(UUID id);

    Tenant insertTenant(Tenant tenant);
    void updateStatus(UUID id, TenantStatus status);
    Optional<Tenant> findById(UUID id);
    boolean slugExists(String slug);
    PageResult<Tenant> list(int page, int size);
    List<String> schemasWithStatus(Collection<TenantStatus> statuses);

    void insertDefaultFeatures(UUID tenantId, Map<String, Boolean> features);
    Map<String, Boolean> features(UUID tenantId);
}
