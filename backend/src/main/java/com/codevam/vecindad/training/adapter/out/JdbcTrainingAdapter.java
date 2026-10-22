package com.codevam.vecindad.training.adapter.out;

import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.persistence.Jdbc;
import com.codevam.vecindad.shared.tenancy.TenantJdbc;
import com.codevam.vecindad.training.application.port.out.TrainingPort;
import com.codevam.vecindad.training.domain.EnrollmentStatus;
import com.codevam.vecindad.training.domain.TrainingEnrollment;
import com.codevam.vecindad.training.domain.TrainingModality;
import com.codevam.vecindad.training.domain.TrainingSession;
import com.codevam.vecindad.training.domain.TrainingStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcTrainingAdapter implements TrainingPort {
    private static final String SESSION_SELECT = """
            SELECT s.id, s.title, s.description, s.trainer_name, s.modality, s.location, s.meeting_link,
                   s.starts_at, s.ends_at, s.capacity, s.status, s.created_at, s.updated_at,
                   (SELECT count(*) FROM {s}.training_enrollments e
                    WHERE e.session_id = s.id AND e.status IN ('ENROLLED','ATTENDED')) AS enrolled_count
            FROM {s}.training_sessions s
            WHERE s.deleted_at IS NULL
            """;

    private static final RowMapper<TrainingSession> SESSION_MAPPER = (rs, i) -> new TrainingSession(
            Jdbc.uuid(rs, "id"), rs.getString("title"), rs.getString("description"), rs.getString("trainer_name"),
            TrainingModality.valueOf(rs.getString("modality")), rs.getString("location"),
            rs.getString("meeting_link"), Jdbc.instant(rs, "starts_at"), Jdbc.instant(rs, "ends_at"),
            rs.getInt("capacity"), TrainingStatus.valueOf(rs.getString("status")), rs.getInt("enrolled_count"),
            Jdbc.instant(rs, "created_at"), Jdbc.instant(rs, "updated_at"));

    private static final String ENROLLMENT_SELECT = """
            SELECT e.id, e.session_id, e.person_id, p.full_name AS person_name, e.unit_id,
                   u.identifier AS unit_identifier, e.status, e.created_at, e.updated_at
            FROM {s}.training_enrollments e
            JOIN {s}.people p ON p.id = e.person_id
            LEFT JOIN {s}.property_units u ON u.id = e.unit_id
            """;

    private static final RowMapper<TrainingEnrollment> ENROLLMENT_MAPPER = (rs, i) -> new TrainingEnrollment(
            Jdbc.uuid(rs, "id"), Jdbc.uuid(rs, "session_id"), Jdbc.uuid(rs, "person_id"),
            rs.getString("person_name"), Jdbc.uuid(rs, "unit_id"), rs.getString("unit_identifier"),
            EnrollmentStatus.valueOf(rs.getString("status")), Jdbc.instant(rs, "created_at"),
            Jdbc.instant(rs, "updated_at"));

    private final TenantJdbc t;

    public JdbcTrainingAdapter(TenantJdbc t) {
        this.t = t;
    }

    @Override
    public Optional<TrainingSession> findSession(UUID id) {
        return t.jdbc().query(t.q(SESSION_SELECT + " AND s.id = ?"), SESSION_MAPPER, id).stream().findFirst();
    }

    @Override
    public PageResult<TrainingSession> searchSessions(TrainingStatus status, Instant from, int page, int size) {
        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        if (status != null) {
            where.append(" AND s.status = ?");
            args.add(status.name());
        }
        if (from != null) {
            where.append(" AND s.starts_at >= ?");
            args.add(Jdbc.ts(from));
        }
        Long total = t.jdbc().queryForObject(t.q("SELECT count(*) FROM {s}.training_sessions s "
                + "WHERE s.deleted_at IS NULL" + where), Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((long) page * size);
        List<TrainingSession> rows = t.jdbc().query(t.q(SESSION_SELECT + where
                        + " ORDER BY s.starts_at ASC LIMIT ? OFFSET ?"), SESSION_MAPPER, pageArgs.toArray());
        return PageResult.of(rows, page, size, total == null ? 0 : total);
    }

    @Override
    public void insertSession(TrainingSession s) {
        t.jdbc().update(t.q("""
                INSERT INTO {s}.training_sessions (id, title, description, trainer_name, modality, location,
                       meeting_link, starts_at, ends_at, capacity, status)
                VALUES (?::uuid, ?::varchar, ?::varchar, ?::varchar, ?::varchar, ?::varchar, ?::varchar,
                        ?::timestamptz, ?::timestamptz, ?, ?::varchar)
                """), s.id(), s.title(), s.description(), s.trainerName(), s.modality().name(), s.location(),
                s.meetingLink(), Jdbc.ts(s.startsAt()), Jdbc.ts(s.endsAt()), s.capacity(), s.status().name());
    }

    @Override
    public boolean updateSession(TrainingSession s) {
        return t.jdbc().update(t.q("""
                UPDATE {s}.training_sessions SET title = ?::varchar, description = ?::varchar,
                       trainer_name = ?::varchar, modality = ?::varchar, location = ?::varchar,
                       meeting_link = ?::varchar, starts_at = ?::timestamptz, ends_at = ?::timestamptz,
                       capacity = ?, updated_at = now()
                WHERE id = ?::uuid AND deleted_at IS NULL
                """), s.title(), s.description(), s.trainerName(), s.modality().name(), s.location(), s.meetingLink(),
                Jdbc.ts(s.startsAt()), Jdbc.ts(s.endsAt()), s.capacity(), s.id()) > 0;
    }

    @Override
    public boolean softDelete(UUID id, UUID by, Instant at) {
        return t.jdbc().update(t.q("""
                UPDATE {s}.training_sessions s SET deleted_at = ?, deleted_by = ?, updated_at = now()
                WHERE s.id = ? AND s.deleted_at IS NULL
                  AND NOT EXISTS (SELECT 1 FROM {s}.training_enrollments e
                                  WHERE e.session_id = s.id AND e.status IN ('ENROLLED','ATTENDED'))
                """), Jdbc.ts(at), by, id) > 0;
    }

    @Override
    public boolean cancelSession(UUID id, UUID by, Instant at) {
        return t.jdbc().update(t.q("UPDATE {s}.training_sessions SET status = 'CANCELLED', updated_at = ?, "
                + "deleted_by = ? WHERE id = ? AND deleted_at IS NULL AND status = 'SCHEDULED'"),
                Jdbc.ts(at), by, id) > 0;
    }

    @Override
    public boolean lockSession(UUID id) {
        return !t.jdbc().query(t.q("SELECT id FROM {s}.training_sessions WHERE id = ? AND deleted_at IS NULL FOR UPDATE"),
                (rs, i) -> rs.getObject("id", UUID.class), id).isEmpty();
    }

    @Override
    public int countEnrolled(UUID sessionId) {
        Integer n = t.jdbc().queryForObject(t.q("SELECT count(*) FROM {s}.training_enrollments "
                + "WHERE session_id = ? AND status IN ('ENROLLED','ATTENDED')"), Integer.class, sessionId);
        return n == null ? 0 : n;
    }

    @Override
    public Optional<TrainingEnrollment> findEnrollment(UUID sessionId, UUID personId) {
        return t.jdbc().query(t.q(ENROLLMENT_SELECT + " WHERE e.session_id = ? AND e.person_id = ?"),
                ENROLLMENT_MAPPER, sessionId, personId).stream().findFirst();
    }

    @Override
    public Optional<TrainingEnrollment> findEnrollmentById(UUID id) {
        return t.jdbc().query(t.q(ENROLLMENT_SELECT + " WHERE e.id = ?"), ENROLLMENT_MAPPER, id).stream().findFirst();
    }

    @Override
    public List<TrainingEnrollment> listEnrollments(UUID sessionId) {
        return t.jdbc().query(t.q(ENROLLMENT_SELECT + " WHERE e.session_id = ? ORDER BY p.full_name"),
                ENROLLMENT_MAPPER, sessionId);
    }

    @Override
    public List<TrainingEnrollment> listEnrollmentsByPerson(UUID personId) {
        return t.jdbc().query(t.q(ENROLLMENT_SELECT + " WHERE e.person_id = ? ORDER BY e.created_at DESC"),
                ENROLLMENT_MAPPER, personId);
    }

    @Override
    public void insertEnrollment(TrainingEnrollment e) {
        t.jdbc().update(t.q("""
                INSERT INTO {s}.training_enrollments (id, session_id, person_id, unit_id, status)
                VALUES (?::uuid, ?::uuid, ?::uuid, ?::uuid, ?::varchar)
                """), e.id(), e.sessionId(), e.personId(), e.unitId(), e.status().name());
    }

    @Override
    public boolean updateEnrollmentStatus(UUID id, EnrollmentStatus status) {
        return t.jdbc().update(t.q("UPDATE {s}.training_enrollments SET status = ?::varchar, updated_at = now() "
                + "WHERE id = ?::uuid"), status.name(), id) > 0;
    }
}
