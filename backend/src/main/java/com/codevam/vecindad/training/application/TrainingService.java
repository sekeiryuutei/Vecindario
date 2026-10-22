package com.codevam.vecindad.training.application;

import com.codevam.vecindad.audit.application.AuditService;
import com.codevam.vecindad.people.application.port.out.PersonPort;
import com.codevam.vecindad.people.domain.Person;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.model.Paging;
import com.codevam.vecindad.shared.security.AuthenticatedUser;
import com.codevam.vecindad.shared.security.CurrentUser;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import com.codevam.vecindad.training.application.port.out.TrainingPort;
import com.codevam.vecindad.training.domain.EnrollmentStatus;
import com.codevam.vecindad.training.domain.TrainingEnrollment;
import com.codevam.vecindad.training.domain.TrainingModality;
import com.codevam.vecindad.training.domain.TrainingSession;
import com.codevam.vecindad.training.domain.TrainingStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TrainingService {
    public record Command(String title, String description, String trainerName, TrainingModality modality,
                          String location, String meetingLink, Instant startsAt, Instant endsAt, int capacity) {}

    public record MyTraining(TrainingSession session, EnrollmentStatus enrollmentStatus) {}

    private final TrainingPort trainings;
    private final PersonPort people;
    private final AuditService audit;
    private final Clock clock;

    public TrainingService(TrainingPort trainings, PersonPort people, AuditService audit, Clock clock) {
        this.trainings = trainings;
        this.people = people;
        this.audit = audit;
        this.clock = clock;
    }

    public PageResult<TrainingSession> search(TrainingStatus status, Instant from, int page, int size) {
        return trainings.searchSessions(status, from, Paging.page(page), Paging.size(size));
    }

    public List<MyTraining> myTrainings() {
        Person person = ownPerson();
        List<TrainingEnrollment> enrollments = trainings.listEnrollmentsByPerson(person.id());
        Map<UUID, EnrollmentStatus> statuses = enrollments.stream()
                .collect(Collectors.toMap(TrainingEnrollment::sessionId, TrainingEnrollment::status, (a, b) -> a));
        return trainings.searchSessions(null, null, 0, 100).content().stream()
                .filter(s -> s.status() != TrainingStatus.CANCELLED || statuses.containsKey(s.id()))
                .map(s -> new MyTraining(s, statuses.get(s.id())))
                .toList();
    }

    public TrainingSession create(Command c) {
        validate(c, null);
        TrainingSession session = new TrainingSession(UUID.randomUUID(), c.title().trim(), trim(c.description()),
                trim(c.trainerName()), c.modality() == null ? TrainingModality.IN_PERSON : c.modality(),
                trim(c.location()), trim(c.meetingLink()), c.startsAt(), c.endsAt(), c.capacity(),
                TrainingStatus.SCHEDULED, 0, null, null);
        trainings.insertSession(session);
        audit.log(TenantContext.require().id(), "TRAINING_CREATED", "TRAINING", session.id().toString(), true,
                Map.of("title", session.title()));
        return getSession(session.id());
    }

    public TrainingSession update(UUID id, Command c) {
        TrainingSession current = getSession(id);
        if (current.status() != TrainingStatus.SCHEDULED) {
            throw ApiException.conflict("TRAINING_NOT_EDITABLE", "Solo se pueden editar capacitaciones programadas.");
        }
        validate(c, current);
        int enrolled = trainings.countEnrolled(id);
        if (c.capacity() < enrolled) {
            throw ApiException.conflict("TRAINING_CAPACITY_BELOW_ENROLLED",
                    "La capacidad no puede ser menor que las personas ya inscritas (" + enrolled + ").");
        }
        TrainingSession updated = new TrainingSession(id, c.title().trim(), trim(c.description()),
                trim(c.trainerName()), c.modality() == null ? current.modality() : c.modality(),
                trim(c.location()), trim(c.meetingLink()), c.startsAt(), c.endsAt(), c.capacity(), current.status(),
                current.enrolledCount(), current.createdAt(), null);
        if (!trainings.updateSession(updated)) {
            throw ApiException.notFound("La capacitación no existe.");
        }
        audit.log(TenantContext.require().id(), "TRAINING_UPDATED", "TRAINING", id.toString(), true, Map.of());
        return getSession(id);
    }

    public void delete(UUID id) {
        getSession(id);
        if (trainings.countEnrolled(id) > 0) {
            throw ApiException.conflict("TRAINING_HAS_ACTIVE_ENROLLMENTS",
                    "No se puede eliminar una capacitación con inscripciones activas.");
        }
        UUID actor = CurrentUser.get().map(AuthenticatedUser::userId).orElse(null);
        if (!trainings.softDelete(id, actor, clock.instant())) {
            throw ApiException.conflict("TRAINING_NOT_DELETABLE",
                    "La capacitación no se puede eliminar en su estado actual.");
        }
        audit.log(TenantContext.require().id(), "TRAINING_DELETED", "TRAINING", id.toString(), true, Map.of());
    }

    public void cancel(UUID id) {
        TrainingSession current = getSession(id);
        UUID actor = CurrentUser.get().map(AuthenticatedUser::userId).orElse(null);
        if (!trainings.cancelSession(id, actor, clock.instant())) {
            throw ApiException.conflict("TRAINING_NOT_CANCELLABLE",
                    "Solo se pueden cancelar capacitaciones programadas.");
        }
        audit.log(TenantContext.require().id(), "TRAINING_CANCELLED", "TRAINING", id.toString(), true,
                Map.of("title", current.title()));
    }

    @Transactional
    public TrainingSession enroll(UUID sessionId) {
        Person person = ownPerson();
        TrainingSession session = getSession(sessionId);
        if (session.status() != TrainingStatus.SCHEDULED) {
            throw ApiException.conflict("TRAINING_NOT_OPEN", "Esta capacitación no está abierta para inscripciones.");
        }
        if (!session.startsAt().isAfter(clock.instant())) {
            throw ApiException.conflict("TRAINING_ALREADY_STARTED", "La capacitación ya comenzó.");
        }
        trainings.lockSession(sessionId);
        if (trainings.countEnrolled(sessionId) >= session.capacity()) {
            throw ApiException.conflict("TRAINING_FULL", "La capacitación ya no tiene cupos disponibles.");
        }
        TrainingEnrollment existing = trainings.findEnrollment(sessionId, person.id()).orElse(null);
        if (existing != null) {
            if (existing.status() == EnrollmentStatus.CANCELLED) {
                trainings.updateEnrollmentStatus(existing.id(), EnrollmentStatus.ENROLLED);
                audit.log(TenantContext.require().id(), "TRAINING_ENROLLED", "TRAINING", sessionId.toString(), true,
                        Map.of("personId", person.id().toString()));
                return getSession(sessionId);
            }
            throw ApiException.conflict("TRAINING_ALREADY_ENROLLED", "Ya estás inscrito en esta capacitación.");
        }
        TrainingEnrollment enrollment = new TrainingEnrollment(UUID.randomUUID(), sessionId, person.id(),
                person.fullName(), null, null, EnrollmentStatus.ENROLLED, null, null);
        try {
            trainings.insertEnrollment(enrollment);
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("TRAINING_ALREADY_ENROLLED", "Ya estás inscrito en esta capacitación.");
        }
        audit.log(TenantContext.require().id(), "TRAINING_ENROLLED", "TRAINING", sessionId.toString(), true,
                Map.of("personId", person.id().toString()));
        return getSession(sessionId);
    }

    public void cancelEnrollment(UUID sessionId) {
        Person person = ownPerson();
        TrainingEnrollment enrollment = trainings.findEnrollment(sessionId, person.id())
                .orElseThrow(() -> ApiException.notFound("No tienes una inscripción en esta capacitación."));
        if (enrollment.status() != EnrollmentStatus.ENROLLED) {
            throw ApiException.conflict("TRAINING_ENROLLMENT_NOT_CANCELLABLE",
                    "Esta inscripción ya no se puede cancelar.");
        }
        TrainingSession session = getSession(sessionId);
        if (!session.startsAt().isAfter(clock.instant())) {
            throw ApiException.conflict("TRAINING_ALREADY_STARTED", "La capacitación ya comenzó.");
        }
        trainings.updateEnrollmentStatus(enrollment.id(), EnrollmentStatus.CANCELLED);
        audit.log(TenantContext.require().id(), "TRAINING_ENROLLMENT_CANCELLED", "TRAINING",
                sessionId.toString(), true, Map.of("personId", person.id().toString()));
    }

    public List<TrainingEnrollment> enrollments(UUID sessionId) {
        getSession(sessionId);
        return trainings.listEnrollments(sessionId);
    }

    public TrainingEnrollment markAttendance(UUID enrollmentId, EnrollmentStatus status) {
        if (status != EnrollmentStatus.ATTENDED && status != EnrollmentStatus.ABSENT) {
            throw ApiException.badRequest("TRAINING_ATTENDANCE_INVALID", "Marca la asistencia como presente o ausente.");
        }
        TrainingEnrollment enrollment = trainings.findEnrollmentById(enrollmentId)
                .orElseThrow(() -> ApiException.notFound("La inscripción no existe."));
        if (enrollment.status() == EnrollmentStatus.CANCELLED) {
            throw ApiException.conflict("TRAINING_ENROLLMENT_CANCELLED", "La inscripción fue cancelada.");
        }
        trainings.updateEnrollmentStatus(enrollmentId, status);
        audit.log(TenantContext.require().id(), "TRAINING_ATTENDANCE_UPDATED", "TRAINING_ENROLLMENT",
                enrollmentId.toString(), true, Map.of("status", status.name()));
        return trainings.findEnrollmentById(enrollmentId).orElseThrow();
    }

    /** Datos de demostración. */
    public TrainingSession seedSession(String title, String description, String trainerName, Instant startsAt,
                                       Instant endsAt, int capacity) {
        TrainingSession session = new TrainingSession(UUID.randomUUID(), title, description, trainerName,
                TrainingModality.IN_PERSON, "Salón comunal", null, startsAt, endsAt, capacity,
                TrainingStatus.SCHEDULED, 0, null, null);
        trainings.insertSession(session);
        return session;
    }

    public void seedEnrollment(UUID sessionId, UUID personId) {
        if (trainings.findEnrollment(sessionId, personId).isPresent()) {
            return;
        }
        Person person = people.findById(personId).orElseThrow();
        trainings.insertEnrollment(new TrainingEnrollment(UUID.randomUUID(), sessionId, personId, person.fullName(),
                null, null, EnrollmentStatus.ENROLLED, null, null));
    }

    private TrainingSession getSession(UUID id) {
        return trainings.findSession(id).orElseThrow(() -> ApiException.notFound("La capacitación no existe."));
    }

    private Person ownPerson() {
        AuthenticatedUser me = CurrentUser.requireTenant();
        return people.findByUserId(me.userId()).orElseThrow(() -> ApiException.forbidden("PERSON_NOT_LINKED",
                "Tu usuario no está vinculado a una persona de la copropiedad."));
    }

    private void validate(Command c, TrainingSession current) {
        if (c.title() == null || c.title().isBlank()) {
            throw ApiException.badRequest("TRAINING_TITLE_REQUIRED", "Escribe el título de la capacitación.");
        }
        if (c.startsAt() == null || c.endsAt() == null) {
            throw ApiException.badRequest("TRAINING_DATES_REQUIRED", "Selecciona la fecha de inicio y de fin.");
        }
        if (!c.endsAt().isAfter(c.startsAt())) {
            throw ApiException.badRequest("TRAINING_DATES_INVALID", "La fecha de fin debe ser posterior al inicio.");
        }
        if (c.capacity() < 1 || c.capacity() > 500) {
            throw ApiException.badRequest("TRAINING_CAPACITY_INVALID", "La capacidad debe estar entre 1 y 500 personas.");
        }
        if (current == null && !c.startsAt().isAfter(clock.instant())) {
            throw ApiException.badRequest("TRAINING_START_IN_PAST", "La capacitación debe iniciar en el futuro.");
        }
        if (current != null && c.startsAt().isBefore(clock.instant())) {
            throw ApiException.badRequest("TRAINING_START_IN_PAST", "La capacitación debe iniciar en el futuro.");
        }
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
