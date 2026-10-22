package com.codevam.vecindad.appointments.adapter.out;

import com.codevam.vecindad.appointments.application.port.out.AppointmentPort;
import com.codevam.vecindad.appointments.domain.Appointment;
import com.codevam.vecindad.appointments.domain.AppointmentModality;
import com.codevam.vecindad.appointments.domain.AppointmentStatus;
import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.persistence.Jdbc;
import com.codevam.vecindad.shared.tenancy.TenantJdbc;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcAppointmentAdapter implements AppointmentPort {
    private static final String SELECT = """
            SELECT a.id, a.unit_id, u.identifier AS unit_identifier, a.person_id, p.full_name AS person_name,
                   a.subject, a.reason, a.scheduled_at, a.duration_minutes, a.modality, a.location, a.meeting_link,
                   a.status, a.admin_notes, a.cancellation_reason, a.created_at, a.updated_at
            FROM {s}.appointments a
            JOIN {s}.property_units u ON u.id = a.unit_id
            JOIN {s}.people p ON p.id = a.person_id
            WHERE a.deleted_at IS NULL
            """;

    private static final RowMapper<Appointment> MAPPER = (rs, i) -> new Appointment(
            Jdbc.uuid(rs, "id"), Jdbc.uuid(rs, "unit_id"), rs.getString("unit_identifier"),
            Jdbc.uuid(rs, "person_id"), rs.getString("person_name"), rs.getString("subject"),
            rs.getString("reason"), Jdbc.instant(rs, "scheduled_at"), rs.getInt("duration_minutes"),
            AppointmentModality.valueOf(rs.getString("modality")), rs.getString("location"),
            rs.getString("meeting_link"), AppointmentStatus.valueOf(rs.getString("status")),
            rs.getString("admin_notes"), rs.getString("cancellation_reason"),
            Jdbc.instant(rs, "created_at"), Jdbc.instant(rs, "updated_at"));

    private final TenantJdbc t;

    public JdbcAppointmentAdapter(TenantJdbc t) {
        this.t = t;
    }

    @Override
    public Optional<Appointment> findById(UUID id) {
        return t.jdbc().query(t.q(SELECT + " AND a.id = ?"), MAPPER, id).stream().findFirst();
    }

    @Override
    public PageResult<Appointment> search(AppointmentStatus status, Instant from, Instant to, String q, int page,
                                          int size) {
        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        if (status != null) {
            where.append(" AND a.status = ?");
            args.add(status.name());
        }
        if (from != null) {
            where.append(" AND a.scheduled_at >= ?");
            args.add(Jdbc.ts(from));
        }
        if (to != null) {
            where.append(" AND a.scheduled_at < ?");
            args.add(Jdbc.ts(to));
        }
        if (q != null) {
            where.append(" AND (lower(a.subject) LIKE ? OR lower(a.reason) LIKE ? "
                    + "OR lower(p.full_name) LIKE ? OR lower(u.identifier) LIKE ?)");
            String like = "%" + q.toLowerCase().trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        Long total = t.jdbc().queryForObject(t.q("SELECT count(*) FROM {s}.appointments a "
                + "JOIN {s}.property_units u ON u.id = a.unit_id JOIN {s}.people p ON p.id = a.person_id "
                + "WHERE a.deleted_at IS NULL" + where), Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((long) page * size);
        List<Appointment> rows = t.jdbc().query(t.q(SELECT + where
                        + " ORDER BY a.scheduled_at ASC, p.full_name LIMIT ? OFFSET ?"),
                MAPPER, pageArgs.toArray());
        return PageResult.of(rows, page, size, total == null ? 0 : total);
    }

    @Override
    public List<Appointment> listByPerson(UUID personId, Instant from) {
        String sql = SELECT + " AND a.person_id = ?" + (from != null ? " AND a.scheduled_at >= ?" : "")
                + " ORDER BY a.scheduled_at DESC";
        return from != null
                ? t.jdbc().query(t.q(sql), MAPPER, personId, Jdbc.ts(from))
                : t.jdbc().query(t.q(sql), MAPPER, personId);
    }

    @Override
    public boolean slotTaken(Instant scheduledAt, UUID excludeId) {
        String sql = "SELECT count(*) FROM {s}.appointments WHERE deleted_at IS NULL "
                + "AND status IN ('REQUESTED','CONFIRMED') AND scheduled_at = ?"
                + (excludeId != null ? " AND id <> ?" : "");
        Long n = excludeId != null
                ? t.jdbc().queryForObject(t.q(sql), Long.class, Jdbc.ts(scheduledAt), excludeId)
                : t.jdbc().queryForObject(t.q(sql), Long.class, Jdbc.ts(scheduledAt));
        return n != null && n > 0;
    }

    @Override
    public void insert(Appointment a) {
        t.jdbc().update(t.q("""
                INSERT INTO {s}.appointments (id, unit_id, person_id, subject, reason, scheduled_at, duration_minutes,
                       modality, location, meeting_link, status, admin_notes, cancellation_reason)
                VALUES (?::uuid, ?::uuid, ?::uuid, ?::varchar, ?::varchar, ?::timestamptz, ?, ?::varchar,
                        ?::varchar, ?::varchar, ?::varchar, ?::varchar, ?::varchar)
                """), a.id(), a.unitId(), a.personId(), a.subject(), a.reason(), Jdbc.ts(a.scheduledAt()),
                a.durationMinutes(), a.modality().name(), a.location(), a.meetingLink(), a.status().name(),
                a.adminNotes(), a.cancellationReason());
    }

    @Override
    public boolean update(Appointment a) {
        return t.jdbc().update(t.q("""
                UPDATE {s}.appointments SET subject = ?::varchar, reason = ?::varchar, scheduled_at = ?::timestamptz,
                       duration_minutes = ?, modality = ?::varchar, location = ?::varchar, meeting_link = ?::varchar,
                       status = ?::varchar, admin_notes = ?::varchar, cancellation_reason = ?::varchar,
                       updated_at = now()
                WHERE id = ?::uuid AND deleted_at IS NULL
                """), a.subject(), a.reason(), Jdbc.ts(a.scheduledAt()), a.durationMinutes(), a.modality().name(),
                a.location(), a.meetingLink(), a.status().name(), a.adminNotes(), a.cancellationReason(), a.id()) > 0;
    }

    @Override
    public boolean softDelete(UUID id, UUID by, Instant at) {
        return t.jdbc().update(t.q("UPDATE {s}.appointments SET deleted_at = ?, deleted_by = ?, updated_at = now() "
                + "WHERE id = ? AND deleted_at IS NULL"), Jdbc.ts(at), by, id) > 0;
    }
}
