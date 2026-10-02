package com.codevam.vecindad.incidents.adapter.out;

import com.codevam.vecindad.incidents.application.port.out.IncidentPort;
import com.codevam.vecindad.incidents.domain.Incident;
import com.codevam.vecindad.incidents.domain.IncidentCategory;
import com.codevam.vecindad.incidents.domain.IncidentStatus;
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
public class JdbcIncidentAdapter implements IncidentPort {
    private static final String COLS = "id, category, description, location, occurred_at, reported_by, assigned_to, status, resolution, closed_at, created_at, updated_at";

    private static final RowMapper<Incident> MAPPER = (rs, i) -> new Incident(Jdbc.uuid(rs, "id"),
            IncidentCategory.valueOf(rs.getString("category")), rs.getString("description"), rs.getString("location"),
            Jdbc.instant(rs, "occurred_at"), Jdbc.uuid(rs, "reported_by"), Jdbc.uuid(rs, "assigned_to"),
            IncidentStatus.valueOf(rs.getString("status")), rs.getString("resolution"), Jdbc.instant(rs, "closed_at"),
            Jdbc.instant(rs, "created_at"), Jdbc.instant(rs, "updated_at"));

    private final TenantJdbc t;

    public JdbcIncidentAdapter(TenantJdbc t) {
        this.t = t;
    }

    @Override
    public Incident insert(Incident i) {
        t.jdbc().update(t.q("INSERT INTO {s}.incidents (id, category, description, location, occurred_at, reported_by) VALUES (?, ?, ?, ?, ?, ?)"),
                i.id(), i.category().name(), i.description(), i.location(), Jdbc.ts(i.occurredAt()), i.reportedBy());
        return findById(i.id()).orElseThrow();
    }

    @Override
    public Optional<Incident> findById(UUID id) {
        return t.jdbc().query(t.q("SELECT " + COLS + " FROM {s}.incidents WHERE id = ?"), MAPPER, id).stream().findFirst();
    }

    @Override
    public PageResult<Incident> search(IncidentCategory category, IncidentStatus status, Instant from, Instant to, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (category != null) { where.append(" AND category = ?"); args.add(category.name()); }
        if (status != null) { where.append(" AND status = ?"); args.add(status.name()); }
        if (from != null) { where.append(" AND occurred_at >= ?"); args.add(Jdbc.ts(from)); }
        if (to != null) { where.append(" AND occurred_at < ?"); args.add(Jdbc.ts(to)); }
        Long total = t.jdbc().queryForObject(t.q("SELECT count(*) FROM {s}.incidents" + where), Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add(page * size);
        List<Incident> rows = t.jdbc().query(t.q("SELECT " + COLS + " FROM {s}.incidents" + where
                + " ORDER BY occurred_at DESC, id LIMIT ? OFFSET ?"), MAPPER, pageArgs.toArray());
        return PageResult.of(rows, page, size, total == null ? 0 : total);
    }

    @Override
    public boolean updateStatus(UUID id, IncidentStatus status, String resolution) {
        return t.jdbc().update(t.q("UPDATE {s}.incidents SET status = ?, resolution = ?, closed_at = CASE WHEN ? = 'CLOSED' THEN now() ELSE NULL END, "
                + "updated_at = now() WHERE id = ?"), status.name(), resolution, status.name(), id) > 0;
    }

    @Override
    public boolean assign(UUID id, UUID userId) {
        return t.jdbc().update(t.q("UPDATE {s}.incidents SET assigned_to = ?, updated_at = now() WHERE id = ?"), userId, id) > 0;
    }
}
