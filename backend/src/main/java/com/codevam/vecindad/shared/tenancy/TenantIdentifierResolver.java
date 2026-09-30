package com.codevam.vecindad.shared.tenancy;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    @Override
    public String resolveCurrentTenantIdentifier() {
        return TenantContext.currentSchema().orElse(SchemaNames.PUBLIC);
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }
}
