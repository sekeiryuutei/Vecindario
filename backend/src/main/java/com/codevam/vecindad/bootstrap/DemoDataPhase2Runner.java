package com.codevam.vecindad.bootstrap;

import com.codevam.vecindad.config.AppProperties;
import com.codevam.vecindad.people.application.PersonService;
import com.codevam.vecindad.people.domain.Person;
import com.codevam.vecindad.people.domain.RelationType;
import com.codevam.vecindad.properties.application.PropertyUnitService;
import com.codevam.vecindad.properties.domain.PropertyUnit;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import com.codevam.vecindad.shared.tenancy.TenantJdbc;
import com.codevam.vecindad.shared.tenancy.TenantRef;
import com.codevam.vecindad.tenancy.application.port.out.TenantPort;
import com.codevam.vecindad.tenancy.domain.Tenant;
import com.codevam.vecindad.vehicles.application.VehicleCatalogService;
import com.codevam.vecindad.vehicles.application.VehicleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Datos demo de personas, relaciones y vehículos. Independiente del seed base para que también se cargue en bases
 * creadas en la Fase 1. Idempotente: solo actúa si la copropiedad demo aún no tiene personas.
 */
@Component
@Order(30)
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DemoDataPhase2Runner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataPhase2Runner.class);

    private final AppProperties props;
    private final TenantPort tenants;
    private final TenantJdbc jdbc;
    private final PersonService people;
    private final PropertyUnitService units;
    private final VehicleService vehicles;
    private final VehicleCatalogService catalog;

    public DemoDataPhase2Runner(AppProperties props, TenantPort tenants, TenantJdbc jdbc, PersonService people,
                                PropertyUnitService units, VehicleService vehicles, VehicleCatalogService catalog) {
        this.props = props;
        this.tenants = tenants;
        this.jdbc = jdbc;
        this.people = people;
        this.units = units;
        this.vehicles = vehicles;
        this.catalog = catalog;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (props.isProduction()) return;
        for (Tenant t : tenants.list(0, 100).content()) {
            if (!"demo_norte".equals(t.slug()) && !"demo_sur".equals(t.slug())) continue;
            TenantContext.runAs(new TenantRef(t.id(), t.schemaName()), () -> {
                Integer n = jdbc.jdbc().queryForObject(jdbc.q("SELECT count(*) FROM {s}.people"), Integer.class);
                if (n != null && n > 0) {
                    return;
                }
                if ("demo_norte".equals(t.slug())) seedNorte(); else seedSur();
                log.info("Datos demo de personas y vehículos cargados en {}", t.slug());
            });
        }
    }

    private void seedNorte() {
        UUID u101 = unit("T1-101"), u102 = unit("T1-102"), u201 = unit("T2-101");
        Person juan = people.create(new PersonService.Command("CC", "1000000001", "Juan Pérez", "propietario@demo-norte.local", "+57 300 000 0001"));
        Person maria = people.create(new PersonService.Command("CC", "1000000002", "María Gómez", "maria.gomez@demo.local", "+57 300 000 0002"));
        Person carlos = people.create(new PersonService.Command("CC", "1000000003", "Carlos Ruiz", "carlos.ruiz@demo.local", "+57 300 000 0003"));
        // Juan es propietario de TRES inmuebles de esta copropiedad; María es arrendataria de uno de ellos.
        people.addRelation(u101, juan.id(), RelationType.OWNER, null);
        people.addRelation(u102, juan.id(), RelationType.OWNER, null);
        people.addRelation(u201, juan.id(), RelationType.OWNER, null);
        people.addRelation(u101, maria.id(), RelationType.TENANT, null);
        people.addRelation(unit("T1-201"), carlos.id(), RelationType.OWNER, null);
        people.linkUser(juan.id(), "propietario@demo-norte.local");

        var park = catalog.createParking("S-01", "SOTANO", u101);
        catalog.createParking("P-PUB-01", "PUBLICO", null);
        // T1-101 tiene sótano privado: puede tener 2 carros (excepción al límite por defecto de 1).
        catalog.setUnitLimit(u101, "CARRO", 2);
        vehicles.create(new VehicleService.Command("CARRO", "ABC123", "Mazda", "3", "Rojo", 2021, juan.id(), u101, park.id(), null));
        vehicles.create(new VehicleService.Command("CARRO", "GHI789", "Renault", "Duster", "Gris", 2019, maria.id(), u101, null, null));
        vehicles.create(new VehicleService.Command("MOTO", "XYZ98A", "Yamaha", "FZ", "Negro", 2022, juan.id(), u101, null, null));
        vehicles.create(new VehicleService.Command("CARRO", "DEF456", "Chevrolet", "Spark", "Blanco", 2018, juan.id(), u102, null, null));
        vehicles.create(new VehicleService.Command("BICICLETA", null, "GW", "Montaña", "Azul", null, carlos.id(), unit("T1-201"), null, null));
    }

    private void seedSur() {
        UUID a101 = unit("A-101");
        // La MISMA persona (mismo usuario) es propietaria también en otra copropiedad.
        Person juan = people.create(new PersonService.Command("CC", "1000000001", "Juan Pérez", "propietario@demo-norte.local", "+57 300 000 0001"));
        people.addRelation(a101, juan.id(), RelationType.OWNER, null);
        people.linkUser(juan.id(), "propietario@demo-norte.local");
        vehicles.create(new VehicleService.Command("CARRO", "JKL012", "Kia", "Rio", "Plata", 2020, juan.id(), a101, null, null));
    }

    private UUID unit(String identifier) {
        return units.search(identifier, null, 0, 10).content().stream()
                .filter(u -> u.identifier().equalsIgnoreCase(identifier)).map(PropertyUnit::id).findFirst()
                .orElseThrow(() -> new IllegalStateException("Inmueble demo no encontrado: " + identifier));
    }
}
