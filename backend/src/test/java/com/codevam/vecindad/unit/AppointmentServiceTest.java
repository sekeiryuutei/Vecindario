package com.codevam.vecindad.unit;

import com.codevam.vecindad.appointments.application.AppointmentService;
import com.codevam.vecindad.appointments.application.port.out.AppointmentPort;
import com.codevam.vecindad.appointments.domain.Appointment;
import com.codevam.vecindad.appointments.domain.AppointmentModality;
import com.codevam.vecindad.appointments.domain.AppointmentStatus;
import com.codevam.vecindad.audit.application.AuditService;
import com.codevam.vecindad.people.application.port.out.PersonPort;
import com.codevam.vecindad.people.application.port.out.UnitRelationPort;
import com.codevam.vecindad.people.domain.Person;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.security.AuthenticatedUser;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import com.codevam.vecindad.shared.tenancy.TenantRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AppointmentServiceTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PERSON_ID = UUID.randomUUID();
    private static final UUID UNIT_ID = UUID.randomUUID();
    private static final UUID APPOINTMENT_ID = UUID.randomUUID();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-21T12:00:00Z"), ZoneOffset.UTC);
    private static final TenantRef TENANT = new TenantRef(TENANT_ID, "tenant_demo_norte");

    private AppointmentPort appointments;
    private PersonPort people;
    private UnitRelationPort relations;
    private AuditService audit;
    private AppointmentService service;

    @BeforeEach
    void setUp() {
        appointments = Mockito.mock(AppointmentPort.class);
        people = Mockito.mock(PersonPort.class);
        relations = Mockito.mock(UnitRelationPort.class);
        audit = Mockito.mock(AuditService.class);
        service = new AppointmentService(appointments, people, relations, audit, CLOCK);
        TenantContext.set(TENANT);
        authenticate();
        when(people.findByUserId(USER_ID)).thenReturn(Optional.of(person()));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void requestCreatesRequestedAppointmentAndAudits() {
        when(relations.userHasUnit(USER_ID, UNIT_ID)).thenReturn(true);
        when(appointments.slotTaken(any(), isNull())).thenReturn(false);
        AtomicReference<Appointment> saved = new AtomicReference<>();
        doAnswer(inv -> {
            saved.set(inv.getArgument(0));
            return null;
        }).when(appointments).insert(any(Appointment.class));
        when(appointments.findById(any())).thenAnswer(inv -> Optional.ofNullable(saved.get()));

        Appointment created = service.request(command(Instant.parse("2026-10-23T14:00:00Z")));

        assertEquals(AppointmentStatus.REQUESTED, created.status());
        assertEquals("Visita a administracion", created.subject());
        assertEquals(UNIT_ID, created.unitId());
        verify(audit).log(eq(TENANT_ID), eq("APPOINTMENT_REQUESTED"), eq("APPOINTMENT"), anyString(), eq(true), anyMap());
    }

    @Test
    void requestRejectsUnitOutsideUserRelations() {
        when(relations.userHasUnit(USER_ID, UNIT_ID)).thenReturn(false);

        ApiException error = assertThrows(ApiException.class,
                () -> service.request(command(Instant.parse("2026-10-23T14:00:00Z"))));

        assertEquals("APPOINTMENT_UNIT_FORBIDDEN", error.getCode());
        verifyNoInteractions(appointments);
    }

    @Test
    void requestRejectsWeekend() {
        when(relations.userHasUnit(USER_ID, UNIT_ID)).thenReturn(true);

        ApiException error = assertThrows(ApiException.class,
                () -> service.request(command(Instant.parse("2026-10-24T14:00:00Z"))));

        assertEquals("APPOINTMENT_OUTSIDE_HOURS", error.getCode());
        verify(appointments, never()).insert(any());
    }

    @Test
    void requestRejectsAppointmentThatEndsAfterClosingTime() {
        when(relations.userHasUnit(USER_ID, UNIT_ID)).thenReturn(true);

        ApiException error = assertThrows(ApiException.class, () -> service.request(
                new AppointmentService.RequestCommand(UNIT_ID, "Visita a administracion",
                        "Consulta sobre el estado de cuenta", Instant.parse("2026-10-23T21:00:00Z"), 120,
                        AppointmentModality.IN_PERSON, "Oficina", null)));

        assertEquals("APPOINTMENT_OUTSIDE_HOURS", error.getCode());
        verify(appointments, never()).insert(any());
    }

    @Test
    void requestRejectsSlotAlreadyTaken() {
        when(relations.userHasUnit(USER_ID, UNIT_ID)).thenReturn(true);
        when(appointments.slotTaken(any(), isNull())).thenReturn(true);

        ApiException error = assertThrows(ApiException.class,
                () -> service.request(command(Instant.parse("2026-10-23T14:00:00Z"))));

        assertEquals("APPOINTMENT_SLOT_TAKEN", error.getCode());
        verify(appointments, never()).insert(any());
    }

    @Test
    void requestRejectsTooSoon() {
        when(relations.userHasUnit(USER_ID, UNIT_ID)).thenReturn(true);

        ApiException error = assertThrows(ApiException.class,
                () -> service.request(command(Instant.parse("2026-10-21T13:00:00Z"))));

        assertEquals("APPOINTMENT_TOO_SOON", error.getCode());
        verify(appointments, never()).insert(any());
    }

    @Test
    void cancelOwnMarksAppointmentCancelled() {
        Appointment current = appointment(AppointmentStatus.REQUESTED);
        when(appointments.findById(APPOINTMENT_ID)).thenReturn(Optional.of(current));
        when(appointments.update(any(Appointment.class))).thenReturn(true);

        service.cancelOwn(APPOINTMENT_ID, "  Sin disponibilidad  ");

        verify(appointments).update(org.mockito.ArgumentMatchers.argThat(a ->
                a.status() == AppointmentStatus.CANCELLED && "Sin disponibilidad".equals(a.cancellationReason())));
        verify(audit).log(eq(TENANT_ID), eq("APPOINTMENT_CANCELLED_BY_RESIDENT"), eq("APPOINTMENT"),
                eq(APPOINTMENT_ID.toString()), eq(true), anyMap());
    }

    @Test
    void cancelOwnRejectsAppointmentOfAnotherPerson() {
        when(appointments.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment(AppointmentStatus.REQUESTED)));
        when(people.findByUserId(USER_ID)).thenReturn(Optional.of(new Person(UUID.randomUUID(), "CC", "123",
                "Otra Persona", null, null, USER_ID, Instant.now(), Instant.now())));

        assertThrows(ApiException.class, () -> service.cancelOwn(APPOINTMENT_ID, "No aplica"));
        verify(appointments, never()).update(any());
    }

    @Test
    void deleteSoftDeletesAndAudits() {
        when(appointments.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment(AppointmentStatus.CANCELLED)));
        when(appointments.softDelete(eq(APPOINTMENT_ID), eq(USER_ID), eq(CLOCK.instant()))).thenReturn(true);

        service.delete(APPOINTMENT_ID);

        verify(audit).log(eq(TENANT_ID), eq("APPOINTMENT_DELETED"), eq("APPOINTMENT"),
                eq(APPOINTMENT_ID.toString()), eq(true), anyMap());
    }

    private void authenticate() {
        AuthenticatedUser user = new AuthenticatedUser(USER_ID, "propietario@demo.local", "Propietario Demo", null,
                TENANT_ID, "tenant_demo_norte", "PROPIETARIO",
                Set.of("APPOINTMENTS_VIEW_OWN", "APPOINTMENTS_REQUEST"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    private static AppointmentService.RequestCommand command(Instant scheduledAt) {
        return new AppointmentService.RequestCommand(UNIT_ID, "  Visita a administracion  ",
                "  Consulta sobre el estado de cuenta  ", scheduledAt, 30, AppointmentModality.IN_PERSON,
                "Oficina", null);
    }

    private static Person person() {
        return new Person(PERSON_ID, "CC", "123456", "Propietario Demo", null, null, USER_ID,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
    }

    private static Appointment appointment(AppointmentStatus status) {
        return new Appointment(APPOINTMENT_ID, UNIT_ID, "T1-101", PERSON_ID, "Propietario Demo",
                "Visita a administracion", "Consulta", Instant.parse("2026-10-23T14:00:00Z"), 30,
                AppointmentModality.IN_PERSON, "Oficina", null, status, null, null,
                Instant.parse("2026-10-21T12:10:00Z"), null);
    }
}
