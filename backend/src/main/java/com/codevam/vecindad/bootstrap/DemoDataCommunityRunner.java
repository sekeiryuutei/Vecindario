package com.codevam.vecindad.bootstrap;

import com.codevam.vecindad.appointments.application.AppointmentService;
import com.codevam.vecindad.appointments.domain.AppointmentStatus;
import com.codevam.vecindad.board.application.BoardService;
import com.codevam.vecindad.board.domain.BoardPosition;
import com.codevam.vecindad.board.domain.BoardStatus;
import com.codevam.vecindad.config.AppProperties;
import com.codevam.vecindad.properties.application.PropertyUnitService;
import com.codevam.vecindad.properties.domain.PropertyUnit;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import com.codevam.vecindad.shared.tenancy.TenantJdbc;
import com.codevam.vecindad.shared.tenancy.TenantRef;
import com.codevam.vecindad.staff.application.StaffService;
import com.codevam.vecindad.tenancy.application.port.out.TenantPort;
import com.codevam.vecindad.tenancy.domain.Tenant;
import com.codevam.vecindad.training.application.TrainingService;
import com.codevam.vecindad.training.domain.TrainingSession;
import com.codevam.vecindad.unitfiles.application.UnitFileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Datos demo de junta directiva, citas, capacitaciones, personal operativo y expedientes.
 * Es idempotente por módulo: cada sección solo se carga si su tabla está vacía.
 */
@Component
@Order(65)
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DemoDataCommunityRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataCommunityRunner.class);
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private final AppProperties props;
    private final TenantPort tenants;
    private final TenantJdbc jdbc;
    private final PropertyUnitService units;
    private final BoardService board;
    private final AppointmentService appointments;
    private final TrainingService trainings;
    private final StaffService staff;
    private final UnitFileService unitFiles;
    private final Clock clock;

    public DemoDataCommunityRunner(AppProperties props, TenantPort tenants, TenantJdbc jdbc,
                                   PropertyUnitService units, BoardService board,
                                   AppointmentService appointments, TrainingService trainings,
                                   StaffService staff, UnitFileService unitFiles, Clock clock) {
        this.props = props;
        this.tenants = tenants;
        this.jdbc = jdbc;
        this.units = units;
        this.board = board;
        this.appointments = appointments;
        this.trainings = trainings;
        this.staff = staff;
        this.unitFiles = unitFiles;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (props.isProduction()) return;
        for (Tenant t : tenants.list(0, 100).content()) {
            if (!"demo_norte".equals(t.slug()) && !"demo_sur".equals(t.slug())) continue;
            TenantContext.runAs(new TenantRef(t.id(), t.schemaName()), () -> {
                if ("demo_norte".equals(t.slug())) seedNorte(); else seedSur();
                log.info("Datos demo comunitarios verificados en {}", t.slug());
            });
        }
    }

    private void seedNorte() {
        UUID juan = person("propietario@demo-norte.local");
        UUID maria = person("maria.gomez@demo.local");
        UUID carlos = person("carlos.ruiz@demo.local");
        UUID t101 = unit("T1-101");
        UUID t102 = unit("T1-102");

        seedBoard(juan, maria, carlos);
        seedAppointments(juan, maria, t101);
        seedTrainings(juan, maria);
        seedStaffNorte();
        unitFiles.seedNote(t101, "Acta de entrega del inmueble",
                "Se recibió el inmueble con medidores funcionales y llaves de puerta principal y de parking.",
                false);
        unitFiles.seedNote(t101, "Seguimiento interno de humedades",
                "Revisión técnica programada para el baño auxiliar. No compartir con residentes hasta el informe.",
                true);
        if (!t102.equals(t101)) {
            unitFiles.seedNote(t102, "Expediente del inmueble",
                    "Inmueble en arriendo. Mantener actualizado el contrato y los datos del arrendatario.",
                    true);
        }
    }

    private void seedSur() {
        UUID juan = person("propietario@demo-norte.local");
        UUID a101 = unit("A-101");
        seedBoard(juan, null, null);
        seedAppointments(juan, null, a101);
        seedTrainings(juan, null);
        seedStaffSur();
        unitFiles.seedNote(a101, "Expediente del inmueble",
                "Inmueble con parqueadero asignado. Documentación de entrega archivada por la administración.",
                false);
    }

    private void seedBoard(UUID president, UUID secretary, UUID treasurer) {
        Integer n = jdbc.jdbc().queryForObject(jdbc.q("SELECT count(*) FROM {s}.board_members"), Integer.class);
        if (n != null && n > 0) {
            return;
        }
        LocalDate start = LocalDate.now(clock).minusMonths(2);
        board.create(new BoardService.Command(president, BoardPosition.PRESIDENTE, start, null, BoardStatus.ACTIVE,
                "Presidente de la junta directiva."));
        if (secretary != null) {
            board.create(new BoardService.Command(secretary, BoardPosition.SECRETARIO, start, null, BoardStatus.ACTIVE,
                    "Secretaría de la junta directiva."));
        }
        if (treasurer != null) {
            board.create(new BoardService.Command(treasurer, BoardPosition.TESORERO, start, null, BoardStatus.ACTIVE,
                    "Tesorero de la junta directiva."));
        }
    }

    private void seedAppointments(UUID person, UUID secondPerson, UUID unitId) {
        Integer n = jdbc.jdbc().queryForObject(jdbc.q("SELECT count(*) FROM {s}.appointments"), Integer.class);
        if (n != null && n > 0) {
            return;
        }
        appointments.seed(person, unitId, "Solicitud de certificado de convivencia",
                "Necesito el certificado para un trámite bancario.",
                nextBusinessDay(1, 10, 0), AppointmentStatus.CONFIRMED);
        if (secondPerson != null) {
            appointments.seed(secondPerson, unitId, "Consulta sobre el contrato de arrendamiento",
                    "Quiero revisar las cláusulas de servicios públicos y parqueadero.",
                    nextBusinessDay(2, 15, 0), AppointmentStatus.REQUESTED);
        }
    }

    private void seedTrainings(UUID firstPerson, UUID secondPerson) {
        Integer n = jdbc.jdbc().queryForObject(jdbc.q("SELECT count(*) FROM {s}.training_sessions"), Integer.class);
        if (n != null && n > 0) {
            return;
        }
        TrainingSession fire = trainings.seedSession("Manejo seguro de extintores",
                "Taller práctico para identificar tipos de fuego, usar extintores y evacuar de forma segura.",
                "Brigada de emergencias", businessTime(7, 9, 0), businessTime(7, 11, 0), 30);
        TrainingSession firstAid = trainings.seedSession("Primeros auxilios comunitarios",
                "Reconocimiento de emergencias, RCP básica y activación de la línea de emergencias.",
                "Cruz Roja local", businessTime(14, 14, 0), businessTime(14, 16, 0), 25);
        trainings.seedEnrollment(fire.id(), firstPerson);
        if (secondPerson != null) {
            trainings.seedEnrollment(firstAid.id(), secondPerson);
        }
    }

    private void seedStaffNorte() {
        Integer n = jdbc.jdbc().queryForObject(jdbc.q("SELECT count(*) FROM {s}.staff_members"), Integer.class);
        if (n != null && n > 0) {
            return;
        }
        staff.seed("Pedro Martínez", "CC", "2000000001", "Portero", "Portería", "+57 310 000 0001",
                "pedro.martinez@demo.local", "Turno rotativo de 12 horas", LocalDate.of(2023, 1, 15));
        staff.seed("Ana Torres", "CC", "2000000002", "Aseadora", "Servicios generales", "+57 310 000 0002",
                "ana.torres@demo.local", "Lunes a sábado, 6:00 a 14:00", LocalDate.of(2022, 8, 1));
        staff.seed("Luis Cárdenas", "CC", "2000000003", "Técnico de mantenimiento", "Mantenimiento",
                "+57 310 000 0003", "luis.cardenas@demo.local", "Lunes a viernes, 8:00 a 17:00",
                LocalDate.of(2024, 2, 5));
    }

    private void seedStaffSur() {
        Integer n = jdbc.jdbc().queryForObject(jdbc.q("SELECT count(*) FROM {s}.staff_members"), Integer.class);
        if (n != null && n > 0) {
            return;
        }
        staff.seed("Jorge Ramírez", "CC", "2000000011", "Portero", "Portería", "+57 311 000 0011",
                "jorge.ramirez@demo.local", "Turno diurno, 6:00 a 18:00", LocalDate.of(2023, 6, 1));
    }

    private UUID person(String email) {
        return jdbc.jdbc().queryForObject(jdbc.q("SELECT id FROM {s}.people WHERE lower(email) = ? "
                + "AND deleted_at IS NULL LIMIT 1"), UUID.class, email.toLowerCase());
    }

    private UUID unit(String identifier) {
        return units.search(identifier, null, 0, 10).content().stream()
                .filter(u -> u.identifier().equalsIgnoreCase(identifier)).map(PropertyUnit::id).findFirst()
                .orElseThrow(() -> new IllegalStateException("Inmueble demo no encontrado: " + identifier));
    }

    /** Próximo día hábil (sin sábados ni domingos) a la hora indicada, en zona horaria de Bogotá. */
    private Instant nextBusinessDay(int businessDaysAhead, int hour, int minute) {
        LocalDate date = LocalDate.now(clock);
        int added = 0;
        while (added < businessDaysAhead) {
            date = date.plusDays(1);
            DayOfWeek day = date.getDayOfWeek();
            if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY) {
                added++;
            }
        }
        return ZonedDateTime.of(date, LocalTime.of(hour, minute), BOGOTA).toInstant();
    }

    /** Día hábil futuro para capacitaciones. */
    private Instant businessTime(int calendarDaysAhead, int hour, int minute) {
        LocalDate date = LocalDate.now(clock).plusDays(calendarDaysAhead);
        while (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            date = date.plusDays(1);
        }
        return ZonedDateTime.of(LocalDateTime.of(date, LocalTime.of(hour, minute)), BOGOTA).toInstant();
    }
}
