package com.codevam.vecindad.vehicles.adapter.out;

import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.persistence.Jdbc;
import com.codevam.vecindad.shared.tenancy.TenantJdbc;
import com.codevam.vecindad.vehicles.application.port.out.AccessPort;
import com.codevam.vecindad.vehicles.domain.AccessEvent;
import com.codevam.vecindad.vehicles.domain.SecurityAlert;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcAccessAdapter implements AccessPort {
    private static final String EVENT_COLS = "id, vehicle_id, unit_id, plate, event_type, occurred_at, guard_user_id, camera_id, source, method, entry_event_id";

    private static final RowMapper<AccessEvent> EVENT = (rs, i) -> new AccessEvent(Jdbc.uuid(rs, "id"), Jdbc.uuid(rs, "vehicle_id"),
            Jdbc.uuid(rs, "unit_id"), rs.getString("plate"), rs.getString("event_type"), Jdbc.instant(rs, "occurred_at"),
            Jdbc.uuid(rs, "guard_user_id"), rs.getString("camera_id"), rs.getString("source"), rs.getString("method"),
            Jdbc.uuid(rs, "entry_event_id"));

    private static final RowMapper<SecurityAlert> ALERT = (rs, i) -> new SecurityAlert(Jdbc.uuid(rs, "id"), rs.getString("alert_type"),
            Jdbc.uuid(rs, "vehicle_id"), rs.getString("plate"), Jdbc.uuid(rs, "unit_id"), rs.getString("message"),
            Jdbc.instant(rs, "created_at"), Jdbc.instant(rs, "resolved_at"));

    private final TenantJdbc t;

    public JdbcAccessAdapter(TenantJdbc t) {
        this.t = t;
    }

    @Override
    public Optional<AccessEvent> registerEntry(UUID vehicleId, UUID guard, String source, String method, String cameraId) {
        // Una sola sentencia: el UPDATE condicional y el INSERT del evento son atómicos aunque haya peticiones simultáneas.
        return t.jdbc().query(t.q("""
                WITH upd AS (
                    UPDATE {s}.vehicles SET presence = 'INSIDE', updated_at = now()
                    WHERE id = ? AND presence = 'OUTSIDE' AND status = 'AUTHORIZED' AND deleted_at IS NULL
                    RETURNING id, unit_id, plate)
                INSERT INTO {s}.vehicle_access_events (id, vehicle_id, unit_id, plate, event_type, guard_user_id, camera_id, source, method)
                SELECT ?::uuid, upd.id, upd.unit_id, upd.plate, 'ENTRY', ?::uuid, ?::varchar, ?::varchar, ?::varchar FROM upd
                RETURNING""" + " " + EVENT_COLS), EVENT, vehicleId, UUID.randomUUID(), guard, cameraId, source, method)
                .stream().findFirst();
    }

    @Override
    public Optional<AccessEvent> registerExit(UUID vehicleId, UUID guard, String source, String method, String cameraId) {
        return t.jdbc().query(t.q("""
                WITH upd AS (
                    UPDATE {s}.vehicles SET presence = 'OUTSIDE', updated_at = now()
                    WHERE id = ? AND presence = 'INSIDE' AND deleted_at IS NULL
                    RETURNING id, unit_id, plate)
                INSERT INTO {s}.vehicle_access_events (id, vehicle_id, unit_id, plate, event_type, guard_user_id, camera_id, source, method, entry_event_id)
                SELECT ?::uuid, upd.id, upd.unit_id, upd.plate, 'EXIT', ?::uuid, ?::varchar, ?::varchar, ?::varchar,
                       (SELECT e.id FROM {s}.vehicle_access_events e
                        WHERE e.vehicle_id = upd.id AND e.event_type = 'ENTRY' ORDER BY e.occurred_at DESC, e.id LIMIT 1)
                FROM upd
                RETURNING""" + " " + EVENT_COLS), EVENT, vehicleId, UUID.randomUUID(), guard, cameraId, source, method)
                .stream().findFirst();
    }

    @Override
    public PageResult<AccessEvent> events(String plate, UUID unitId, Instant from, Instant to, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (plate != null) {
            where.append(" AND upper(plate) LIKE ?");
            args.add("%" + plate.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        }
        if (unitId != null) { where.append(" AND unit_id = ?"); args.add(unitId); }
        if (from != null) { where.append(" AND occurred_at >= ?"); args.add(Jdbc.ts(from)); }
        if (to != null) { where.append(" AND occurred_at < ?"); args.add(Jdbc.ts(to)); }
        Long total = t.jdbc().queryForObject(t.q("SELECT count(*) FROM {s}.vehicle_access_events" + where), Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add(page * size);
        List<AccessEvent> rows = t.jdbc().query(t.q("SELECT " + EVENT_COLS + " FROM {s}.vehicle_access_events" + where
                + " ORDER BY occurred_at DESC, id LIMIT ? OFFSET ?"), EVENT, pageArgs.toArray());
        return PageResult.of(rows, page, size, total == null ? 0 : total);
    }

    @Override
    public void saveAlert(String type, UUID vehicleId, String plate, UUID unitId, String message, UUID guard) {
        t.jdbc().update(t.q("INSERT INTO {s}.security_alerts (id, alert_type, vehicle_id, plate, unit_id, message, guard_user_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)"), UUID.randomUUID(), type, vehicleId, plate, unitId, message, guard);
    }

    @Override
    public PageResult<SecurityAlert> alerts(boolean onlyOpen, int page, int size) {
        String where = onlyOpen ? " WHERE resolved_at IS NULL" : "";
        Long total = t.jdbc().queryForObject(t.q("SELECT count(*) FROM {s}.security_alerts" + where), Long.class);
        List<SecurityAlert> rows = t.jdbc().query(t.q("SELECT id, alert_type, vehicle_id, plate, unit_id, message, created_at, resolved_at "
                + "FROM {s}.security_alerts" + where + " ORDER BY created_at DESC, id LIMIT ? OFFSET ?"), ALERT, size, page * size);
        return PageResult.of(rows, page, size, total == null ? 0 : total);
    }

    @Override
    public boolean resolveAlert(UUID id, UUID by) {
        return t.jdbc().update(t.q("UPDATE {s}.security_alerts SET resolved_at = now(), resolved_by = ? "
                + "WHERE id = ? AND resolved_at IS NULL"), by, id) > 0;
    }
}
