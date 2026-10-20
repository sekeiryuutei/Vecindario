package com.codevam.vecindad.bootstrap;

import com.codevam.vecindad.config.AppProperties;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import com.codevam.vecindad.shared.tenancy.TenantJdbc;
import com.codevam.vecindad.shared.tenancy.TenantRef;
import com.codevam.vecindad.tenancy.application.port.out.TenantPort;
import com.codevam.vecindad.tenancy.domain.Tenant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** Empleado y parámetros de nómina de demostración en Demo Norte. Idempotente. */
@Component
@Order(62)
public class DemoDataPayrollRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataPayrollRunner.class);

    private final AppProperties props;
    private final TenantPort tenants;
    private final TenantJdbc jdbc;

    public DemoDataPayrollRunner(AppProperties props, TenantPort tenants, TenantJdbc jdbc) {
        this.props = props;
        this.tenants = tenants;
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!props.seed().enabled()) return;
        for (Tenant tenant : tenants.list(0, 100).content()) {
            if (!"demo_norte".equals(tenant.slug())) continue;
            TenantContext.runAs(new TenantRef(tenant.id(), tenant.schemaName()), () -> {
                Integer count = jdbc.jdbc().queryForObject(
                        jdbc.q("SELECT count(*) FROM {s}.payroll_employees"), Integer.class);
                if (count != null && count > 0) return;
                jdbc.jdbc().update(jdbc.q("""
                        UPDATE {s}.payroll_settings
                           SET transport_allowance = 200000.00,
                               minimum_wage = 1300000.00
                         WHERE id = 1
                        """));
                jdbc.jdbc().update(jdbc.q("""
                        INSERT INTO {s}.payroll_employees
                            (document_type, document_number, full_name, position, base_salary, start_date,
                             status, cost_center, fixed_deductions, notes)
                        VALUES ('CC', '1000000100', 'Ana María Torres', 'Aseadora', 1500000.00, ?,
                                'ACTIVE', 'ADMINISTRACION', 0.00, 'Empleado de demostración')
                        """), LocalDate.of(2024, 1, 15));
                log.info("Datos demo de nómina cargados en {}", tenant.slug());
            });
        }
    }
}
