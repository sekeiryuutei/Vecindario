package com.codevam.vecindad.bootstrap;

import com.codevam.vecindad.assemblies.application.AssemblyService;
import com.codevam.vecindad.assemblies.domain.Assembly;
import com.codevam.vecindad.assemblies.domain.AssemblyType;
import com.codevam.vecindad.assemblies.domain.VotingType;
import com.codevam.vecindad.communications.application.CommunicationService;
import com.codevam.vecindad.communications.domain.CommunicationPriority;
import com.codevam.vecindad.communications.domain.TargetAudience;
import com.codevam.vecindad.config.AppProperties;
import com.codevam.vecindad.pqrs.application.PqrService;
import com.codevam.vecindad.pqrs.domain.PqrCategory;
import com.codevam.vecindad.pqrs.domain.PqrPriority;
import com.codevam.vecindad.properties.application.PropertyUnitService;
import com.codevam.vecindad.properties.domain.PropertyUnit;
import com.codevam.vecindad.reservations.application.CommonAreaService;
import com.codevam.vecindad.reservations.domain.CommonArea;
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
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/** Datos demo Fase 4: Zonas comunes, PQRS, Comunicados, Asambleas. Idempotente. */
@Component
@Order(55)
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DemoDataPhase4Runner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataPhase4Runner.class);

    private final AppProperties props;
    private final TenantPort tenants;
    private final TenantJdbc jdbc;
    private final CommonAreaService commonAreaService;
    private final PqrService pqrService;
    private final CommunicationService communicationService;
    private final AssemblyService assemblyService;
    private final PropertyUnitService units;

    public DemoDataPhase4Runner(AppProperties props, TenantPort tenants, TenantJdbc jdbc,
                                 CommonAreaService commonAreaService, PqrService pqrService,
                                 CommunicationService communicationService, AssemblyService assemblyService,
                                 PropertyUnitService units) {
        this.props = props;
        this.tenants = tenants;
        this.jdbc = jdbc;
        this.commonAreaService = commonAreaService;
        this.pqrService = pqrService;
        this.communicationService = communicationService;
        this.assemblyService = assemblyService;
        this.units = units;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (props.isProduction()) return;
        for (Tenant t : tenants.list(0, 100).content()) {
            if (!"demo_norte".equals(t.slug()) && !"demo_sur".equals(t.slug())) continue;
            TenantContext.runAs(new TenantRef(t.id(), t.schemaName()), () -> {
                Integer n = jdbc.jdbc().queryForObject(jdbc.q("SELECT count(*) FROM {s}.common_areas"), Integer.class);
                if (n != null && n > 0) return;
                seed(t.id(), "demo_norte".equals(t.slug()));
                log.info("Datos demo Fase 4 cargados en {}", t.slug());
            });
        }
    }

    private void seed(UUID tenantId, boolean norte) {
        // 1. Zonas comunes
        CommonArea salon = commonAreaService.create(new CommonArea(
                null, "Salón Comunal", "Salón social para eventos y reuniones familiares", 50,
                true, new BigDecimal("50000.00"), new BigDecimal("100000.00"), true,
                LocalTime.of(8, 0), LocalTime.of(23, 0), 60, 5, 2, 12, 30, 24,
                "El salón se entrega barrido y con el mobiliario inventariado. El residente responde por daños y debe dejar el espacio en las mismas condiciones. No se permite música por encima de los decibeles permitidos ni el ingreso de personas ajenas a la copropiedad sin registro.",
                null, null, true, null, null
        ));

        CommonArea bbq = commonAreaService.create(new CommonArea(
                null, "Zona BBQ", "Quiosco con asador y mobiliario rústico", 20,
                true, new BigDecimal("30000.00"), new BigDecimal("50000.00"), false,
                LocalTime.of(10, 0), LocalTime.of(20, 0), 60, 4, 2, 6, 20, 12,
                "La zona BBQ debe quedar limpia, con la parrilla apagada y los residuos en los recipientes dispuestos para ello. El uso después de las 18:00 requiere aprobación de la administración para controlar el ruido.",
                LocalTime.of(18, 0), LocalTime.of(20, 0), true, null, null
        ));

        CommonArea cancha = commonAreaService.create(new CommonArea(
                null, "Cancha Múltiple", "Cancha deportiva para microfútbol y baloncesto", 15,
                false, BigDecimal.ZERO, BigDecimal.ZERO, false,
                LocalTime.of(6, 0), LocalTime.of(21, 0), 60, 2, 2, 2, 7, 6,
                "La cancha se usa con calzado deportivo y sin ingreso de alimentos o bebidas alcohólicas. El uso entre las 18:00 y las 21:00 requiere autorización de la administración por tratarse de horario nocturno; no genera cobro.",
                LocalTime.of(18, 0), LocalTime.of(21, 0), true, null, null
        ));

        // 2. PQRS y Convivencia
        UUID u101 = unit(norte ? "T1-101" : "A-101");
        UUID adminUserId = UUID.fromString("00000000-0000-0000-0000-000000000002"); // admin demo

        pqrService.create(adminUserId, new PqrService.CreatePqrCommand(
                u101, null, PqrCategory.CONVIVENCIA_RUIDO, PqrPriority.HIGH,
                "Música a alto volumen en horario nocturno",
                "Se solicita mediación preventiva de convivencia por música a alto volumen posterior a las 11:00 PM (Art. 33 Ley 1801).",
                false
        ));

        // 3. Comunicado
        communicationService.create(adminUserId, new CommunicationService.CreateCommunicationCommand(
                "Convocatoria Asamblea General 2026",
                "Se convoca a todos los copropietarios a la asamblea ordinaria anual",
                "Estimados copropietarios:\nPor medio de la presente se convoca a la reunión ordinaria de asamblea general conforme a la Ley 675 de 2001.",
                CommunicationPriority.IMPORTANT,
                TargetAudience.ALL,
                null,
                true,
                Instant.now().plus(30, ChronoUnit.DAYS),
                true
        ));

        // 4. Asamblea
        Assembly asm = assemblyService.createAssembly(new AssemblyService.CreateAssemblyCommand(
                "Asamblea General Ordinaria 2026",
                AssemblyType.ORDINARIA,
                Instant.now().plus(15, ChronoUnit.DAYS),
                "Salón Comunal Principal",
                false,
                null,
                new BigDecimal("51.00")
        ));

        assemblyService.createTopic(asm.id(), new AssemblyService.CreateTopicCommand(
                1, "Aprobación del Presupuesto 2026",
                "Sometimiento a votación del presupuesto de ingresos y gastos presentado por la administración.",
                VotingType.COEFFICIENT,
                false,
                List.of("A FAVOR", "EN CONTRA", "ABSTENCION")
        ));
    }

    private UUID unit(String identifier) {
        return units.search(identifier, null, 0, 10).content().stream().filter(u -> u.identifier().equalsIgnoreCase(identifier))
                .map(PropertyUnit::id).findFirst().orElseThrow(() -> new IllegalStateException("Inmueble demo no encontrado: " + identifier));
    }
}

