package com.codevam.vecindad.identity.domain;

import java.util.UUID;

/** Acceso activo verificado de un usuario a una copropiedad (incluye schema: uso interno, nunca se expone en la API). */
public record TenantAccess(UUID tenantId, String tenantName, String slug, String schemaName,
                           UUID companyId, String roleCode) {}
