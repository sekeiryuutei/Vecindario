package com.codevam.vecindad.appointments.application;

import com.codevam.vecindad.appointments.application.port.out.AppointmentPort;
import com.codevam.vecindad.appointments.domain.Appointment;
import com.codevam.vecindad.appointments.domain.AppointmentModality;
import com.codevam.vecindad.appointments.domain.AppointmentStatus;
import com.codevam.vecindad.audit.application.AuditService;
import com.codevam.vecindad.people.application.port.out.PersonPort;
import com.codevam.vecindad.people.application.port.out.UnitRelationPort;
import com.codevam.vecindad.people.domain.Person;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.model.Paging;
import com.codevam.vecindad.shared.security.AuthenticatedUser;
import com.codevam.vecindad.shared.security.CurrentUser;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AppointmentService {
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final LocalTime OPENING = LocalTime.of(8, 0);
    private static final LocalTime CLOSING = LocalTime.of(17, 0);

    public record RequestCommand(UUID unitId, String subject, String reason, Instant scheduledAt,
                                 Integer durationMinutes, AppointmentModality modality,
                                 String location, String meetingLink) {}

    public record UpdateCommand(String subject, String reason, Instant scheduledAt, Integer durationMinutes,
                                AppointmentModality modality, String location, String meetingLink,
                                AppointmentStatus status, String adminNotes) {}

    private final AppointmentPort appointments;
    private final PersonPort people;
    private final UnitRelationPort relations;
    private final AuditService audit;
    private final Clock clock;

    public AppointmentService(AppointmentPort appointments, PersonPort people, UnitRelationPort relations,
                              AuditService audit, Clock clock) {
        this.appointments = appointments;
        this.people = people;
        this.relations = relations;
        this.audit = audit;
        this.clock = clock;
    }

    public PageResult<Appointment> search(AppointmentStatus status, Instant from, Instant to, String q,
                                          int page, int size) {
        return appointments.search(status, from, to, clean(q), Paging.page(page), Paging.size(size));
    }

    public List<Appointment> myAppointments() {
        Person person = ownPerson();
        return appointments.listByPerson(person.id(), null);
    }

    public Appointment request(RequestCommand c) {
        AuthenticatedUser me = CurrentUser.requireTenant();
        Person person = ownPerson();
        if (c.unitId() == null || !relations.userHasUnit(me.userId(), c.unitId())) {
            throw ApiException.forbidden("APPOINTMENT_UNIT_FORBIDDEN",
                    "Solo puedes solicitar citas para un inmueble donde resides.");
        }
        validateContent(c.subject(), c.reason());
        Instant scheduledAt = c.scheduledAt();
        int duration = c.durationMinutes() == null ? 30 : c.durationMinutes();
        validateDuration(duration);
        validateSlot(scheduledAt, duration, null);
        AppointmentModality modality = c.modality() == null ? AppointmentModality.IN_PERSON : c.modality();
        Appointment appointment = new Appointment(UUID.randomUUID(), c.unitId(), null, person.id(), person.fullName(),
                c.subject().trim(), c.reason().trim(), scheduledAt, duration, modality, trim(c.location()),
                trim(c.meetingLink()), AppointmentStatus.REQUESTED, null, null, null, null);
        insert(appointment);
        audit.log(TenantContext.require().id(), "APPOINTMENT_REQUESTED", "APPOINTMENT",
                appointment.id().toString(), true, Map.of("personId", person.id().toString()));
        return get(appointment.id());
    }

    public Appointment update(UUID id, UpdateCommand c) {
        Appointment current = get(id);
        String subject = c.subject() == null || c.subject().isBlank() ? current.subject() : c.subject().trim();
        String reason = c.reason() == null || c.reason().isBlank() ? current.reason() : c.reason().trim();
        validateContent(subject, reason);
        Instant scheduledAt = c.scheduledAt() == null ? current.scheduledAt() : c.scheduledAt();
        int duration = c.durationMinutes() == null ? current.durationMinutes() : c.durationMinutes();
        validateDuration(duration);
        AppointmentModality modality = c.modality() == null ? current.modality() : c.modality();
        AppointmentStatus status = c.status() == null ? current.status() : c.status();
        if (status == AppointmentStatus.REQUESTED || status == AppointmentStatus.CONFIRMED) {
            validateSlot(scheduledAt, duration, id);
        }
        Appointment updated = new Appointment(current.id(), current.unitId(), current.unitIdentifier(),
                current.personId(), current.personName(), subject, reason, scheduledAt, duration, modality,
                c.location() == null ? current.location() : trim(c.location()),
                c.meetingLink() == null ? current.meetingLink() : trim(c.meetingLink()),
                status, c.adminNotes() == null ? current.adminNotes() : trim(c.adminNotes()),
                status == AppointmentStatus.CANCELLED ? current.cancellationReason() : null,
                current.createdAt(), null);
        persist(updated);
        audit.log(TenantContext.require().id(), "APPOINTMENT_UPDATED", "APPOINTMENT", id.toString(), true,
                Map.of("status", status.name()));
        return get(id);
    }

    public void cancelOwn(UUID id, String reason) {
        Person person = ownPerson();
        Appointment current = get(id);
        if (!current.personId().equals(person.id())) {
            throw ApiException.notFound("La cita no existe.");
        }
        if (current.status() != AppointmentStatus.REQUESTED && current.status() != AppointmentStatus.CONFIRMED) {
            throw ApiException.conflict("APPOINTMENT_NOT_CANCELLABLE", "Esta cita ya no se puede cancelar.");
        }
        if (!current.scheduledAt().isAfter(clock.instant())) {
            throw ApiException.conflict("APPOINTMENT_IN_PAST", "La cita ya ocurrió y no se puede cancelar.");
        }
        Appointment cancelled = new Appointment(current.id(), current.unitId(), current.unitIdentifier(),
                current.personId(), current.personName(), current.subject(), current.reason(), current.scheduledAt(),
                current.durationMinutes(), current.modality(), current.location(), current.meetingLink(),
                AppointmentStatus.CANCELLED, current.adminNotes(), trim(reason), current.createdAt(), null);
        persist(cancelled);
        audit.log(TenantContext.require().id(), "APPOINTMENT_CANCELLED_BY_RESIDENT", "APPOINTMENT",
                id.toString(), true, Map.of());
    }

    public void delete(UUID id) {
        Appointment current = get(id);
        UUID actor = CurrentUser.get().map(AuthenticatedUser::userId).orElse(null);
        if (!appointments.softDelete(id, actor, clock.instant())) {
            throw ApiException.notFound("La cita no existe.");
        }
        audit.log(TenantContext.require().id(), "APPOINTMENT_DELETED", "APPOINTMENT", id.toString(), true,
                Map.of("status", current.status().name()));
    }

    /** Datos de demostración: no requiere un usuario autenticado. */
    public Appointment seed(UUID personId, UUID unitId, String subject, String reason, Instant scheduledAt,
                            AppointmentStatus status) {
        validateSlot(scheduledAt, 30, null);
        Appointment appointment = new Appointment(UUID.randomUUID(), unitId, null, personId, null,
                subject, reason, scheduledAt, 30, AppointmentModality.IN_PERSON, "Oficina de administración",
                null, status, null, null, null, null);
        insert(appointment);
        return appointment;
    }

    private void insert(Appointment appointment) {
        try {
            appointments.insert(appointment);
        } catch (DuplicateKeyException e) {
            throw slotConflict();
        }
    }

    private void persist(Appointment appointment) {
        try {
            if (!appointments.update(appointment)) {
                throw ApiException.notFound("La cita no existe.");
            }
        } catch (DuplicateKeyException e) {
            throw slotConflict();
        }
    }

    private Appointment get(UUID id) {
        return appointments.findById(id).orElseThrow(() -> ApiException.notFound("La cita no existe."));
    }

    private Person ownPerson() {
        AuthenticatedUser me = CurrentUser.requireTenant();
        return people.findByUserId(me.userId()).orElseThrow(() -> ApiException.forbidden("PERSON_NOT_LINKED",
                "Tu usuario no está vinculado a una persona de la copropiedad."));
    }

    private void validateContent(String subject, String reason) {
        if (subject == null || subject.isBlank()) {
            throw ApiException.badRequest("APPOINTMENT_SUBJECT_REQUIRED", "Escribe el asunto de la cita.");
        }
        if (reason == null || reason.isBlank()) {
            throw ApiException.badRequest("APPOINTMENT_REASON_REQUIRED", "Describe el motivo de la cita.");
        }
        if (subject.length() > 160 || reason.length() > 1000) {
            throw ApiException.badRequest("APPOINTMENT_TEXT_TOO_LONG", "El asunto o el motivo exceden el tamaño permitido.");
        }
    }

    private void validateSlot(Instant scheduledAt, int duration, UUID excludeId) {
        if (scheduledAt == null) {
            throw ApiException.badRequest("APPOINTMENT_DATE_REQUIRED", "Selecciona la fecha y hora de la cita.");
        }
        Instant now = clock.instant();
        if (!scheduledAt.isAfter(now.plusSeconds(7200))) {
            throw ApiException.badRequest("APPOINTMENT_TOO_SOON",
                    "La cita debe solicitarse con al menos dos horas de anticipación.");
        }
        if (scheduledAt.isAfter(now.plusSeconds(60L * 24 * 60 * 60))) {
            throw ApiException.badRequest("APPOINTMENT_TOO_FAR",
                    "Solo puedes solicitar citas dentro de los próximos 60 días.");
        }
        ZonedDateTime local = scheduledAt.atZone(BOGOTA);
        DayOfWeek day = local.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            throw ApiException.badRequest("APPOINTMENT_OUTSIDE_HOURS",
                    "Las citas se atienden de lunes a viernes, de 8:00 a 17:00.");
        }
        LocalTime time = local.toLocalTime();
        if (time.isBefore(OPENING) || !time.isBefore(CLOSING)
                || time.plusMinutes(duration).isAfter(CLOSING)) {
            throw ApiException.badRequest("APPOINTMENT_OUTSIDE_HOURS",
                    "Las citas se atienden de lunes a viernes, de 8:00 a 17:00.");
        }
        if (appointments.slotTaken(scheduledAt, excludeId)) {
            throw slotConflict();
        }
    }

    private static void validateDuration(int duration) {
        if (duration < 15 || duration > 120) {
            throw ApiException.badRequest("APPOINTMENT_DURATION_INVALID",
                    "La duración debe estar entre 15 y 120 minutos.");
        }
    }

    private static ApiException slotConflict() {
        return ApiException.conflict("APPOINTMENT_SLOT_TAKEN",
                "Ya existe una cita en ese horario. Elige otra hora.");
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
