package com.codevam.vecindad.unitfiles.adapter.out;

import com.codevam.vecindad.shared.persistence.Jdbc;
import com.codevam.vecindad.shared.tenancy.TenantJdbc;
import com.codevam.vecindad.unitfiles.application.port.out.UnitFileNotePort;
import com.codevam.vecindad.unitfiles.domain.UnitFileNote;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcUnitFileNoteAdapter implements UnitFileNotePort {
    private static final String SELECT = """
            SELECT id, unit_id, title, body, internal, created_by, created_at, updated_at
            FROM {s}.unit_file_notes WHERE unit_id = ? AND deleted_at IS NULL
            """;

    private static final RowMapper<UnitFileNote> MAPPER = (rs, i) -> new UnitFileNote(
            Jdbc.uuid(rs, "id"), Jdbc.uuid(rs, "unit_id"), rs.getString("title"), rs.getString("body"),
            rs.getBoolean("internal"), Jdbc.uuid(rs, "created_by"), Jdbc.instant(rs, "created_at"),
            Jdbc.instant(rs, "updated_at"));

    private final TenantJdbc t;

    public JdbcUnitFileNoteAdapter(TenantJdbc t) {
        this.t = t;
    }

    @Override
    public List<UnitFileNote> listByUnit(UUID unitId, boolean includeInternal) {
        String sql = SELECT + (includeInternal ? "" : " AND internal = false") + " ORDER BY created_at DESC";
        return t.jdbc().query(t.q(sql), MAPPER, unitId);
    }

    @Override
    public Optional<UnitFileNote> findById(UUID id, UUID unitId) {
        return t.jdbc().query(t.q(SELECT + " AND id = ?"), MAPPER, unitId, id).stream().findFirst();
    }

    @Override
    public void insert(UnitFileNote n) {
        t.jdbc().update(t.q("""
                INSERT INTO {s}.unit_file_notes (id, unit_id, title, body, internal, created_by)
                VALUES (?::uuid, ?::uuid, ?::varchar, ?::varchar, ?, ?::uuid)
                """), n.id(), n.unitId(), n.title(), n.body(), n.internal(), n.createdBy());
    }

    @Override
    public boolean update(UnitFileNote n) {
        return t.jdbc().update(t.q("""
                UPDATE {s}.unit_file_notes SET title = ?::varchar, body = ?::varchar, internal = ?,
                       updated_at = now()
                WHERE id = ?::uuid AND unit_id = ?::uuid AND deleted_at IS NULL
                """), n.title(), n.body(), n.internal(), n.id(), n.unitId()) > 0;
    }

    @Override
    public boolean softDelete(UUID id, UUID unitId, UUID by, Instant at) {
        return t.jdbc().update(t.q("UPDATE {s}.unit_file_notes SET deleted_at = ?, deleted_by = ?, updated_at = now() "
                + "WHERE id = ? AND unit_id = ? AND deleted_at IS NULL"), Jdbc.ts(at), by, id, unitId) > 0;
    }
}
