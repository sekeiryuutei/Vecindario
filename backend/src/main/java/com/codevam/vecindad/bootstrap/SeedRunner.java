package com.codevam.vecindad.bootstrap;

import com.codevam.vecindad.config.AppProperties;
import com.codevam.vecindad.identity.application.port.out.MembershipPort;
import com.codevam.vecindad.identity.application.port.out.UserPort;
import com.codevam.vecindad.identity.domain.AccessEvent;
import com.codevam.vecindad.identity.domain.Roles;
import com.codevam.vecindad.identity.domain.User;
import com.codevam.vecindad.identity.domain.UserStatus;
import com.codevam.vecindad.properties.application.PropertyUnitService;
import com.codevam.vecindad.properties.domain.UnitStatus;
import com.codevam.vecindad.properties.domain.UnitType;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import com.codevam.vecindad.shared.tenancy.TenantRef;
import com.codevam.vecindad.tenancy.application.TenantAdminService;
import com.codevam.vecindad.tenancy.application.port.out.TenantPort;
import com.codevam.vecindad.tenancy.domain.Company;
import com.codevam.vecindad.tenancy.domain.Tenant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/** Datos de demostración (solo desarrollo). Idempotente: si ya existe demo_norte no hace nada. */
@Component
@Order(20)
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class SeedRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);

    private final AppProperties props;
    private final TenantAdminService tenantAdmin;
    private final TenantPort tenants;
    private final UserPort users;
    private final MembershipPort memberships;
    private final PropertyUnitService properties;
    private final PasswordEncoder encoder;

    public SeedRunner(AppProperties props, TenantAdminService tenantAdmin, TenantPort tenants, UserPort users,
                      MembershipPort memberships, PropertyUnitService properties, PasswordEncoder encoder) {
        this.props = props;
        this.tenantAdmin = tenantAdmin;
        this.tenants = tenants;
        this.users = users;
        this.memberships = memberships;
        this.properties = properties;
        this.encoder = encoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (props.isProduction()) {
            log.warn("APP_SEED_ENABLED=true ignorado: el seed no se ejecuta en producción.");
            return;
        }
        if (tenants.slugExists("demo_norte")) {
            log.info("Seed omitido: los datos de demostración ya existen.");
            return;
        }
        Company company = tenantAdmin.createCompany("CodeVam Administración Demo", "900123456-7");
        Tenant norte = tenantAdmin.createTenant(new TenantAdminService.NewTenant(company.id(), "Conjunto Demo Norte",
                "901000001-1", "demo_norte", "Calle 10 # 20-30", "Cali", "Valle del Cauca", "+57 602 000 0001", "admin@demo-norte.local"));
        Tenant sur = tenantAdmin.createTenant(new TenantAdminService.NewTenant(company.id(), "Conjunto Demo Sur",
                "901000002-2", "demo_sur", "Carrera 50 # 5-15", "Cali", "Valle del Cauca", "+57 602 000 0002", "admin@demo-sur.local"));

        String hash = encoder.encode(props.seed().password());
        user("superadmin@vecindad.local", "Super Administrador Plataforma", hash, Roles.SUPER_ADMIN_PLATFORM);

        User admin = user("admin@vecindad.local", "Andrea Administradora", hash, null);
        grant(admin, norte, Roles.ADMINISTRADOR);
        grant(admin, sur, Roles.ADMINISTRADOR);

        grant(user("secretaria@demo-norte.local", "Sofía Secretaria", hash, null), norte, Roles.SECRETARIA_ADMINISTRACION);
        grant(user("contador@demo-norte.local", "Camilo Contador", hash, null), norte, Roles.CONTADOR);
        grant(user("consejo@demo-norte.local", "Carolina Consejera", hash, null), norte, Roles.CONSEJO);
        grant(user("portero@demo-norte.local", "Pedro Portero", hash, null), norte, Roles.PORTERO);
        // Una misma persona es propietaria en dos copropiedades distintas (multi-tenant real).
        User owner = user("propietario@demo-norte.local", "Juan Pérez (propietario)", hash, null);
        grant(owner, norte, Roles.PROPIETARIO);
        grant(owner, sur, Roles.PROPIETARIO);
        grant(user("admin.sur@demo-sur.local", "Sergio Administrador Sur", hash, null), sur, Roles.ADMINISTRADOR);

        seedUnits(norte, new String[]{"T1", "T2"}, 3, 2);
        seedParking(norte, 3);
        seedUnits(sur, new String[]{"A"}, 3, 2);
        log.info("Seed de demostración completado (empresa, 2 copropiedades, usuarios y inmuebles).");
    }

    private User user(String email, String name, String hash, String platformRole) {
        return users.findByEmail(email).orElseGet(() -> users.insert(email, name, hash, UserStatus.ACTIVE, platformRole));
    }

    private void grant(User user, Tenant tenant, String role) {
        memberships.grant(user.id(), tenant.id(), role, null);
        memberships.addHistory(user.id(), tenant.id(), AccessEvent.GRANTED, role, null, null);
    }

    private void seedUnits(Tenant t, String[] towers, int floors, int perFloor) {
        TenantContext.runAs(new TenantRef(t.id(), t.schemaName()), () -> {
            for (String tower : towers) {
                for (int floor = 1; floor <= floors; floor++) {
                    for (int n = 1; n <= perFloor; n++) {
                        String number = floor + "0" + n;
                        properties.create(new PropertyUnitService.Command(UnitType.APARTAMENTO, tower + "-" + number,
                                number, tower, floor, new BigDecimal("1.250000"), new BigDecimal("72.50"), UnitStatus.ACTIVE));
                    }
                }
            }
        });
    }

    private void seedParking(Tenant t, int count) {
        TenantContext.runAs(new TenantRef(t.id(), t.schemaName()), () -> {
            for (int i = 1; i <= count; i++) {
                properties.create(new PropertyUnitService.Command(UnitType.PARQUEADERO, String.format("P-%03d", i),
                        String.valueOf(i), null, null, null, new BigDecimal("12.50"), UnitStatus.ACTIVE));
            }
        });
    }
}
