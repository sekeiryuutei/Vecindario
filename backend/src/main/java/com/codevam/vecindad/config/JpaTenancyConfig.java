package com.codevam.vecindad.config;

import com.codevam.vecindad.shared.tenancy.SchemaPerTenantConnectionProvider;
import com.codevam.vecindad.shared.tenancy.TenantIdentifierResolver;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JpaTenancyConfig {

    @Bean
    public HibernatePropertiesCustomizer multiTenancyCustomizer(SchemaPerTenantConnectionProvider provider,
                                                                TenantIdentifierResolver resolver) {
        return props -> {
            props.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, provider);
            props.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolver);
        };
    }
}
