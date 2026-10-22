package com.codevam.vecindad.unit;

import com.codevam.vecindad.audit.application.AuditService;
import com.codevam.vecindad.people.application.port.out.PersonPort;
import com.codevam.vecindad.people.domain.Person;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.security.AuthenticatedUser;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import com.codevam.vecindad.shared.tenancy.TenantRef;
import com.codevam.vecindad.training.application.TrainingService;
import com.codevam.vecindad.training.application.port.out.TrainingPort;
import com.codevam.vecindad.training.domain.EnrollmentStatus;
import com.codevam.vecindad.training.domain.TrainingEnrollment;
import com.codevam.vecindad.training.domain.TrainingModality;
import com.codevam.vecindad.training.domain.TrainingSession;
import com.codevam.vecindad.training.domain.TrainingStatus;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TrainingServiceTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PERSON_ID = UUID.randomUUID();
    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final UUID ENROLLMENT_ID = UUID.randomUUID();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-21T12:00:00Z"), ZoneOffset.UTC);
    private static final TenantRef TENANT = new TenantRef(TENANT_ID, "tenant_demo_norte");

    private TrainingPort trainings;
    private PersonPort people;
    private AuditService audit;
    private TrainingService service;

    @BeforeEach
    void setUp() {
        trainings = Mockito.mock(TrainingPort.class);
        people = Mockito.mock(PersonPort.class);
        audit = Mockito.mock(AuditService.class);
        service = new TrainingService(trainings, people, audit, CLOCK);
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
    void createRegistersScheduledTraining() {
        AtomicReference<TrainingSession> saved = new AtomicReference<>();
        doAnswer(inv -> {
            saved.set(inv.getArgument(0));
            return null;
        }).when(trainings).insertSession(any(TrainingSession.class));
        when(trainings.findSession(any())).thenAnswer(inv -> Optional.ofNullable(saved.get()));

        TrainingSession created = service.create(command(10, Instant.parse("2026-11-10T14:00:00Z")));

        assertEquals(TrainingStatus.SCHEDULED, created.status());
        assertEquals("Manejo de extintores", created.title());
        assertEquals(10, created.capacity());
        verify(audit).log(eq(TENANT_ID), eq("TRAINING_CREATED"), eq("TRAINING"), anyString(), eq(true), anyMap());
    }

    @Test
    void createRejectsStartInPast() {
        ApiException error = assertThrows(ApiException.class,
                () -> service.create(command(10, Instant.parse("2026-10-20T14:00:00Z"))));

        assertEquals("TRAINING_START_IN_PAST", error.getCode());
        verifyNoInteractions(trainings);
    }

    @Test
    void updateRejectsCapacityBelowEnrolled() {
        when(trainings.findSession(SESSION_ID)).thenReturn(Optional.of(session(10, TrainingStatus.SCHEDULED)));
        when(trainings.countEnrolled(SESSION_ID)).thenReturn(5);

        ApiException error = assertThrows(ApiException.class,
                () -> service.update(SESSION_ID, command(4, Instant.parse("2026-11-10T14:00:00Z"))));

        assertEquals("TRAINING_CAPACITY_BELOW_ENROLLED", error.getCode());
        verify(trainings, never()).updateSession(any());
    }

    @Test
    void enrollRegistersPersonWhenCapacityAvailable() {
        when(trainings.findSession(SESSION_ID)).thenReturn(Optional.of(session(10, TrainingStatus.SCHEDULED)));
        when(trainings.countEnrolled(SESSION_ID)).thenReturn(0);
        when(trainings.findEnrollment(SESSION_ID, PERSON_ID)).thenReturn(Optional.empty());

        service.enroll(SESSION_ID);

        verify(trainings).lockSession(SESSION_ID);
        verify(trainings).insertEnrollment(org.mockito.ArgumentMatchers.argThat(e ->
                e.sessionId().equals(SESSION_ID) && e.personId().equals(PERSON_ID)
                        && e.status() == EnrollmentStatus.ENROLLED));
        verify(audit).log(eq(TENANT_ID), eq("TRAINING_ENROLLED"), eq("TRAINING"),
                eq(SESSION_ID.toString()), eq(true), anyMap());
    }

    @Test
    void enrollRejectsFullTraining() {
        when(trainings.findSession(SESSION_ID)).thenReturn(Optional.of(session(2, TrainingStatus.SCHEDULED)));
        when(trainings.countEnrolled(SESSION_ID)).thenReturn(2);

        ApiException error = assertThrows(ApiException.class, () -> service.enroll(SESSION_ID));

        assertEquals("TRAINING_FULL", error.getCode());
        verify(trainings, never()).insertEnrollment(any());
    }

    @Test
    void enrollRestoresCancelledEnrollment() {
        TrainingEnrollment cancelled = enrollment(EnrollmentStatus.CANCELLED);
        when(trainings.findSession(SESSION_ID)).thenReturn(Optional.of(session(10, TrainingStatus.SCHEDULED)));
        when(trainings.countEnrolled(SESSION_ID)).thenReturn(0);
        when(trainings.findEnrollment(SESSION_ID, PERSON_ID)).thenReturn(Optional.of(cancelled));

        service.enroll(SESSION_ID);

        verify(trainings).updateEnrollmentStatus(ENROLLMENT_ID, EnrollmentStatus.ENROLLED);
        verify(trainings, never()).insertEnrollment(any());
    }

    @Test
    void deleteRejectsTrainingWithActiveEnrollments() {
        when(trainings.findSession(SESSION_ID)).thenReturn(Optional.of(session(10, TrainingStatus.SCHEDULED)));
        when(trainings.countEnrolled(SESSION_ID)).thenReturn(1);

        ApiException error = assertThrows(ApiException.class, () -> service.delete(SESSION_ID));

        assertEquals("TRAINING_HAS_ACTIVE_ENROLLMENTS", error.getCode());
        verify(trainings, never()).softDelete(any(), any(), any());
    }

    @Test
    void deleteSoftDeletesTrainingWithoutEnrollments() {
        when(trainings.findSession(SESSION_ID)).thenReturn(Optional.of(session(10, TrainingStatus.CANCELLED)));
        when(trainings.countEnrolled(SESSION_ID)).thenReturn(0);
        when(trainings.softDelete(eq(SESSION_ID), eq(USER_ID), eq(CLOCK.instant()))).thenReturn(true);

        service.delete(SESSION_ID);

        verify(audit).log(eq(TENANT_ID), eq("TRAINING_DELETED"), eq("TRAINING"),
                eq(SESSION_ID.toString()), eq(true), anyMap());
    }

    @Test
    void markAttendanceRejectsInvalidStatus() {
        ApiException error = assertThrows(ApiException.class,
                () -> service.markAttendance(ENROLLMENT_ID, EnrollmentStatus.CANCELLED));

        assertEquals("TRAINING_ATTENDANCE_INVALID", error.getCode());
        verifyNoInteractions(trainings);
    }

    @Test
    void markAttendanceUpdatesEnrollment() {
        when(trainings.findEnrollmentById(ENROLLMENT_ID))
                .thenReturn(Optional.of(enrollment(EnrollmentStatus.ENROLLED)))
                .thenReturn(Optional.of(enrollment(EnrollmentStatus.ATTENDED)));
        when(trainings.updateEnrollmentStatus(ENROLLMENT_ID, EnrollmentStatus.ATTENDED)).thenReturn(true);

        TrainingEnrollment result = service.markAttendance(ENROLLMENT_ID, EnrollmentStatus.ATTENDED);

        assertEquals(EnrollmentStatus.ATTENDED, result.status());
        verify(audit).log(eq(TENANT_ID), eq("TRAINING_ATTENDANCE_UPDATED"), eq("TRAINING_ENROLLMENT"),
                eq(ENROLLMENT_ID.toString()), eq(true), anyMap());
    }

    private void authenticate() {
        AuthenticatedUser user = new AuthenticatedUser(USER_ID, "propietario@demo.local", "Propietario Demo", null,
                TENANT_ID, "tenant_demo_norte", "PROPIETARIO",
                Set.of("TRAININGS_VIEW_OWN", "TRAININGS_ENROLL_OWN"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    private static TrainingService.Command command(int capacity, Instant startsAt) {
        return new TrainingService.Command("  Manejo de extintores  ", "  Taller practico  ",
                "  Brigada  ", TrainingModality.IN_PERSON, "Salon comunal", null, startsAt,
                startsAt.plusSeconds(7200), capacity);
    }

    private static Person person() {
        return new Person(PERSON_ID, "CC", "123456", "Propietario Demo", null, null, USER_ID,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
    }

    private static TrainingSession session(int capacity, TrainingStatus status) {
        return new TrainingSession(SESSION_ID, "Manejo de extintores", "Taller", "Brigada",
                TrainingModality.IN_PERSON, "Salon comunal", null, Instant.parse("2026-11-10T14:00:00Z"),
                Instant.parse("2026-11-10T16:00:00Z"), capacity, status, 0,
                Instant.parse("2026-10-21T12:00:00Z"), null);
    }

    private static TrainingEnrollment enrollment(EnrollmentStatus status) {
        return new TrainingEnrollment(ENROLLMENT_ID, SESSION_ID, PERSON_ID, "Propietario Demo", null, null,
                status, Instant.parse("2026-10-21T12:00:00Z"), null);
    }
}
