package com.codevam.vecindad.tenancy.domain;

import java.time.Instant;
import java.util.UUID;

public record Tenant(UUID id, UUID companyId, String name, String nit, String slug, String schemaName,
                     String address, String city, String department, String phone, String email,
                     String currency, String timezone, String locale, TenantStatus status, Instant createdAt) {}
