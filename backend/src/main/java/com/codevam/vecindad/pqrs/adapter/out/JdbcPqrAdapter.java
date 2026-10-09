package com.codevam.vecindad.pqrs.adapter.out;

import com.codevam.vecindad.pqrs.application.port.out.PqrPort;
import com.codevam.vecindad.pqrs.domain.*;
import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.persistence.Jdbc;
import com.codevam.vecindad.shared.tenancy.TenantJdbc;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcPqrAdapter implements PqrPort {

    private static final String PQR_COLS = """
            id, ticket_number, unit_id, person_id, user_id, category, priority, status,
            title, description, is_anonymous, assigned_to, sla_target_hours, sla_due_at,
            resolved_at, closed_at, resolution_notes, created_at, updated_at
            """;

    private static final RowMapper<Pqr> PQR_MAPPER = (rs, rowNum) -> new Pqr(
            Jdbc.uuid(rs, "id"),
            rs.getString("ticket_number"),
            Jdbc.uuid(rs, "unit_id"),
            Jdbc.uuid(rs, "person_id"),
            Jdbc.uuid(rs, "user_id"),
            PqrCategory.valueOf(rs.getString("category")),
            PqrPriority.valueOf(rs.getString("priority")),
            PqrStatus.valueOf(rs.getString("status")),
            rs.getString("title"),
            rs.getString("description"),
            rs.getBoolean("is_anonymous"),
            Jdbc.uuid(rs, "assigned_to"),
            rs.getInt("sla_target_hours"),
            Jdbc.instant(rs, "sla_due_at"),
            Jdbc.instant(rs, "resolved_at"),
            Jdbc.instant(rs, "closed_at"),
            rs.getString("resolution_notes"),
            Jdbc.instant(rs, "created_at"),
            Jdbc.instant(rs, "updated_at")
    );

    private static final RowMapper<PqrMessage> MSG_MAPPER = (rs, rowNum) -> new PqrMessage(
            Jdbc.uuid(rs, "id"),
            Jdbc.uuid(rs, "pqr_id"),
            Jdbc.uuid(rs, "sender_user_id"),
            rs.getString("sender_name"),
            rs.getBoolean("is_internal"),
            rs.getString("message"),
            Jdbc.instant(rs, "created_at")
    );

    private final TenantJdbc t;

    public JdbcPqrAdapter(TenantJdbc t) {
        this.t = t;
    }

    @Override
    public String nextTicketNumber() {
        Long nextVal = t.jdbc().queryForObject(t.q("SELECT nextval('{s}.pqr_ticket_seq')"), Long.class);
        long val = nextVal != null ? nextVal : 1L;
        return "PQR-" + Year.now().getValue() + "-" + String.format("%04d", val);
    }

    @Override
    public Pqr insert(Pqr pqr) {
        UUID id = pqr.id() == null ? UUID.randomUUID() : pqr.id();
        String sql = """
                INSERT INTO {s}.pqrs (
                    id, ticket_number, unit_id, person_id, user_id, category, priority, status,
                    title, description, is_anonymous, assigned_to, sla_target_hours, sla_due_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        t.jdbc().update(t.q(sql),
                id, pqr.ticketNumber(), pqr.unitId(), pqr.personId(), pqr.userId(),
                pqr.category().name(), pqr.priority().name(), pqr.status().name(),
                pqr.title(), pqr.description(), pqr.isAnonymous(), pqr.assignedTo(),
                pqr.slaTargetHours(), Jdbc.ts(pqr.slaDueAt()));
        return findById(id).orElseThrow();
    }

    @Override
    public Optional<Pqr> findById(UUID id) {
        return t.jdbc().query(t.q("SELECT " + PQR_COLS + " FROM {s}.pqrs WHERE id = ?"), PQR_MAPPER, id)
                .stream().findFirst();
    }

    @Override
    public PageResult<Pqr> search(PqrCategory category, PqrStatus status, PqrPriority priority,
                                  Boolean overdue, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (category != null) {
            where.append(" AND category = ?");
            args.add(category.name());
        }
        if (status != null) {
            where.append(" AND status = ?");
            args.add(status.name());
        }
        if (priority != null) {
            where.append(" AND priority = ?");
            args.add(priority.name());
        }
        if (overdue != null) {
            if (overdue) {
                where.append(" AND status IN ('OPEN', 'IN_PROGRESS') AND sla_due_at < now()");
            } else {
                where.append(" AND (status NOT IN ('OPEN', 'IN_PROGRESS') OR sla_due_at >= now())");
            }
        }

        Long total = t.jdbc().queryForObject(t.q("SELECT count(*) FROM {s}.pqrs" + where), Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add(page * size);
        List<Pqr> rows = t.jdbc().query(t.q("SELECT " + PQR_COLS + " FROM {s}.pqrs" + where
                + " ORDER BY created_at DESC LIMIT ? OFFSET ?"), PQR_MAPPER, pageArgs.toArray());
        return PageResult.of(rows, page, size, total == null ? 0 : total);
    }

    @Override
    public PageResult<Pqr> findByUser(UUID userId, int page, int size) {
        Long total = t.jdbc().queryForObject(t.q("SELECT count(*) FROM {s}.pqrs WHERE user_id = ?"), Long.class, userId);
        List<Pqr> rows = t.jdbc().query(t.q("SELECT " + PQR_COLS + " FROM {s}.pqrs WHERE user_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?"),
                PQR_MAPPER, userId, size, page * size);
        return PageResult.of(rows, page, size, total == null ? 0 : total);
    }

    @Override
    public boolean updateStatus(UUID id, PqrStatus status, String resolutionNotes, Instant resolvedAt, Instant closedAt) {
        String sql = """
                UPDATE {s}.pqrs SET
                    status = ?,
                    resolution_notes = COALESCE(?, resolution_notes),
                    resolved_at = COALESCE(?, resolved_at),
                    closed_at = COALESCE(?, closed_at),
                    updated_at = now()
                WHERE id = ?
                """;
        return t.jdbc().update(t.q(sql),
                status.name(), resolutionNotes, Jdbc.ts(resolvedAt), Jdbc.ts(closedAt), id) > 0;
    }

    @Override
    public boolean assign(UUID id, UUID assignedUserId) {
        return t.jdbc().update(t.q("UPDATE {s}.pqrs SET assigned_to = ?, updated_at = now() WHERE id = ?"),
                assignedUserId, id) > 0;
    }

    @Override
    public PqrMessage addMessage(PqrMessage msg) {
        UUID id = msg.id() == null ? UUID.randomUUID() : msg.id();
        String sql = """
                INSERT INTO {s}.pqrs_messages (id, pqr_id, sender_user_id, sender_name, is_internal, message)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        t.jdbc().update(t.q(sql), id, msg.pqrId(), msg.senderUserId(), msg.senderName(), msg.isInternal(), msg.message());
        return t.jdbc().queryForObject(t.q("SELECT id, pqr_id, sender_user_id, sender_name, is_internal, message, created_at FROM {s}.pqrs_messages WHERE id = ?"),
                MSG_MAPPER, id);
    }

    @Override
    public List<PqrMessage> listMessages(UUID pqrId, boolean includeInternal) {
        String sql = "SELECT id, pqr_id, sender_user_id, sender_name, is_internal, message, created_at FROM {s}.pqrs_messages WHERE pqr_id = ?"
                + (includeInternal ? "" : " AND is_internal = false")
                + " ORDER BY created_at ASC";
        return t.jdbc().query(t.q(sql), MSG_MAPPER, pqrId);
    }

    @Override
    public PqrSlaMetrics getSlaMetrics() {
        String sql = """
                SELECT
                    COUNT(*) FILTER (WHERE status IN ('OPEN', 'IN_PROGRESS')) AS total_open,
                    COUNT(*) FILTER (WHERE status IN ('OPEN', 'IN_PROGRESS') AND sla_due_at >= now()) AS on_time,
                    COUNT(*) FILTER (WHERE status IN ('OPEN', 'IN_PROGRESS') AND sla_due_at < now()) AS overdue,
                    COALESCE(AVG(EXTRACT(EPOCH FROM (resolved_at - created_at)) / 3600.0) FILTER (WHERE resolved_at IS NOT NULL), 0.0) AS avg_hours
                FROM {s}.pqrs
                """;
        return t.jdbc().queryForObject(t.q(sql), (rs, rowNum) -> new PqrSlaMetrics(
                rs.getLong("total_open"),
                rs.getLong("on_time"),
                rs.getLong("overdue"),
                rs.getDouble("avg_hours")
        ));
    }
}

