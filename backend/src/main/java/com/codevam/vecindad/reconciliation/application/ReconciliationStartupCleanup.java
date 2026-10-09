package com.codevam.vecindad.reconciliation.application;

import com.codevam.vecindad.reconciliation.application.port.out.ReconciliationPort;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import com.codevam.vecindad.shared.tenancy.TenantRef;
import com.codevam.vecindad.tenancy.application.port.out.TenantPort;
import com.codevam.vecindad.tenancy.domain.Tenant;
import com.codevam.vecindad.tenancy.domain.TenantStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Las importaciones en curso se pierden con un reinicio (el archivo vive en memoria): se marcan FALLIDAS para poder cargarlas de nuevo. */
@Component
@Order(15)
public class ReconciliationStartupCleanup implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ReconciliationStartupCleanup.class);

    private final TenantPort tenants;
    private final ReconciliationPort port;

    public ReconciliationStartupCleanup(TenantPort tenants, ReconciliationPort port) {
        this.tenants = tenants;
        this.port = port;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (Tenant t : tenants.list(0, 1000).content()) {
            if (t.status() != TenantStatus.ACTIVE && t.status() != TenantStatus.SUSPENDED) continue;
            TenantContext.runAs(new TenantRef(t.id(), t.schemaName()), () -> {
                int n = port.failStaleImports();
                if (n > 0) log.warn("{} importaciones bancarias interrumpidas marcadas como FAILED en {}", n, t.slug());
            });
        }
    }
}
