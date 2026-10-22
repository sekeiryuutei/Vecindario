package com.codevam.vecindad.bootstrap;

import com.codevam.vecindad.config.AppProperties;
import com.codevam.vecindad.pets.application.PetService;
import com.codevam.vecindad.pets.domain.PetStatus;
import com.codevam.vecindad.pets.domain.Species;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Mascotas de demostración para las copropiedades demo. Independiente de los demás seed para que también se
 * cargue en bases ya existentes. Idempotente: solo actúa si la copropiedad aún no tiene mascotas.
 */
@Component
@Order(35)
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DemoDataPetsRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataPetsRunner.class);

    private final AppProperties props;
    private final TenantPort tenants;
    private final TenantJdbc jdbc;
    private final PropertyUnitService units;
    private final PetService pets;

    public DemoDataPetsRunner(AppProperties props, TenantPort tenants, TenantJdbc jdbc, PropertyUnitService units,
                              PetService pets) {
        this.props = props;
        this.tenants = tenants;
        this.jdbc = jdbc;
        this.units = units;
        this.pets = pets;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (props.isProduction()) return;
        for (Tenant t : tenants.list(0, 100).content()) {
            if (!"demo_norte".equals(t.slug()) && !"demo_sur".equals(t.slug())) continue;
            TenantContext.runAs(new TenantRef(t.id(), t.schemaName()), () -> {
                Integer n = jdbc.jdbc().queryForObject(jdbc.q("SELECT count(*) FROM {s}.pets"), Integer.class);
                if (n != null && n > 0) {
                    return;
                }
                if ("demo_norte".equals(t.slug())) seedNorte(); else seedSur();
                log.info("Mascotas demo cargadas en {}", t.slug());
            });
        }
    }

    private void seedNorte() {
        UUID u101 = unit("T1-101");
        pets.create(new PetService.Command("Luna", Species.PERRO, "Labrador", "Dorado", LocalDate.of(2021, 3, 15),
                new BigDecimal("28.50"), "985141000123456", true, LocalDate.now().plusYears(1),
                "Vacunas al día. Esterilizada.", PetStatus.ACTIVE, u101));
        pets.create(new PetService.Command("Michi", Species.GATO, "Criollo", "Naranja", LocalDate.of(2022, 7, 1),
                new BigDecimal("4.20"), null, false, null, "Gata de interior.", PetStatus.ACTIVE, u101));
    }

    private void seedSur() {
        UUID a101 = unit("A-101");
        pets.create(new PetService.Command("Rocky", Species.PERRO, "Beagle", "Tricolor", LocalDate.of(2020, 11, 2),
                new BigDecimal("12.80"), "985141000654321", true, LocalDate.now().plusMonths(8),
                "Vacunas al día.", PetStatus.ACTIVE, a101));
    }

    private UUID unit(String identifier) {
        return units.search(identifier, null, 0, 10).content().stream()
                .filter(u -> u.identifier().equalsIgnoreCase(identifier)).map(PropertyUnit::id).findFirst()
                .orElseThrow(() -> new IllegalStateException("Inmueble demo no encontrado: " + identifier));
    }
}
