package com.codevam.vecindad.bootstrap;

import com.codevam.vecindad.config.AppProperties;
import com.codevam.vecindad.identity.application.port.out.UserPort;
import com.codevam.vecindad.incidents.application.IncidentService;
import com.codevam.vecindad.incidents.domain.IncidentCategory;
import com.codevam.vecindad.parcels.application.ParcelService;
import com.codevam.vecindad.properties.application.PropertyUnitService;
import com.codevam.vecindad.properties.domain.PropertyUnit;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import com.codevam.vecindad.shared.tenancy.TenantJdbc;
import com.codevam.vecindad.shared.tenancy.TenantRef;
import com.codevam.vecindad.tenancy.application.port.out.TenantPort;
import com.codevam.vecindad.tenancy.domain.Tenant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Datos demo de paquetes y novedades en Demo Norte. Idempotente (solo si aún no hay paquetes). */
@Component
@Order(40)
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DemoDataPhase2bRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataPhase2bRunner.class);

    private final AppProperties props;
    private final TenantPort tenants;
    private final TenantJdbc jdbc;
    private final ParcelService parcels;
    private final IncidentService incidents;
    private final PropertyUnitService units;
    private final UserPort users;

    public DemoDataPhase2bRunner(AppProperties props, TenantPort tenants, TenantJdbc jdbc, ParcelService parcels,
                                 IncidentService incidents, PropertyUnitService units, UserPort users) {
        this.props = props;
        this.tenants = tenants;
        this.jdbc = jdbc;
        this.parcels = parcels;
        this.incidents = incidents;
        this.units = units;
        this.users = users;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (props.isProduction()) return;
        UUID guard = users.findByEmail("portero@demo-norte.local").map(u -> u.id()).orElse(null);
        if (guard == null) return;
        for (Tenant t : tenants.list(0, 100).content()) {
            if (!"demo_norte".equals(t.slug())) continue;
            TenantContext.runAs(new TenantRef(t.id(), t.schemaName()), () -> {
                Integer n = jdbc.jdbc().queryForObject(jdbc.q("SELECT count(*) FROM {s}.packages"), Integer.class);
                if (n != null && n > 0) return;
                UUID u101 = units.search("T1-101", null, 0, 10).content().stream()
                        .filter(u -> u.identifier().equalsIgnoreCase("T1-101")).map(PropertyUnit::id).findFirst().orElse(null);
                if (u101 == null) return;
                parcels.receive(new ParcelService.ReceiveCommand(u101, "Juan Pérez", "Servientrega", "SV-1001", "Caja mediana"));
                parcels.receive(new ParcelService.ReceiveCommand(u101, "María Gómez", "Coordinadora", "CO-2002", "Sobre"));
                incidents.create(guard, new IncidentService.Command(IncidentCategory.INFRAESTRUCTURA,
                        "Luminaria del parqueadero de visitantes sin funcionar.", "Parqueadero visitantes", null));
                log.info("Datos demo de paquetes y novedades cargados en {}", t.slug());
            });
        }
    }
}
