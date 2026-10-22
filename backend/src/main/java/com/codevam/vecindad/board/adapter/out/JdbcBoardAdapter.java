package com.codevam.vecindad.board.adapter.out;

import com.codevam.vecindad.board.application.port.out.BoardPort;
import com.codevam.vecindad.board.domain.BoardMember;
import com.codevam.vecindad.board.domain.BoardPosition;
import com.codevam.vecindad.board.domain.BoardStatus;
import com.codevam.vecindad.shared.persistence.Jdbc;
import com.codevam.vecindad.shared.tenancy.TenantJdbc;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcBoardAdapter implements BoardPort {
    private static final String SELECT = """
            SELECT b.id, b.person_id, p.full_name AS person_name, p.document_number, p.email, p.phone,
                   b.position, b.start_date, b.end_date, b.status, b.notes, b.created_at, b.updated_at
            FROM {s}.board_members b JOIN {s}.people p ON p.id = b.person_id
            WHERE b.deleted_at IS NULL
            """;

    private static final RowMapper<BoardMember> MAPPER = (rs, i) -> new BoardMember(
            Jdbc.uuid(rs, "id"), Jdbc.uuid(rs, "person_id"), rs.getString("person_name"),
            rs.getString("document_number"), rs.getString("email"), rs.getString("phone"),
            BoardPosition.valueOf(rs.getString("position")),
            rs.getObject("start_date", LocalDate.class), rs.getObject("end_date", LocalDate.class),
            BoardStatus.valueOf(rs.getString("status")), rs.getString("notes"),
            Jdbc.instant(rs, "created_at"), Jdbc.instant(rs, "updated_at"));

    private final TenantJdbc t;

    public JdbcBoardAdapter(TenantJdbc t) {
        this.t = t;
    }

    @Override
    public Optional<BoardMember> findById(UUID id) {
        return t.jdbc().query(t.q(SELECT + " AND b.id = ?"), MAPPER, id).stream().findFirst();
    }

    @Override
    public List<BoardMember> list(BoardStatus status) {
        String sql = SELECT + (status != null ? " AND b.status = ?" : "")
                + " ORDER BY b.status, b.start_date DESC, p.full_name";
        return status != null
                ? t.jdbc().query(t.q(sql), MAPPER, status.name())
                : t.jdbc().query(t.q(sql), MAPPER);
    }

    @Override
    public boolean activePersonExists(UUID personId, UUID excludeId) {
        String sql = "SELECT count(*) FROM {s}.board_members WHERE deleted_at IS NULL AND status = 'ACTIVE' "
                + "AND person_id = ?" + (excludeId != null ? " AND id <> ?" : "");
        Long n = excludeId != null
                ? t.jdbc().queryForObject(t.q(sql), Long.class, personId, excludeId)
                : t.jdbc().queryForObject(t.q(sql), Long.class, personId);
        return n != null && n > 0;
    }

    @Override
    public boolean activePositionExists(BoardPosition position, UUID excludeId) {
        String sql = "SELECT count(*) FROM {s}.board_members WHERE deleted_at IS NULL AND status = 'ACTIVE' "
                + "AND position = ?" + (excludeId != null ? " AND id <> ?" : "");
        Long n = excludeId != null
                ? t.jdbc().queryForObject(t.q(sql), Long.class, position.name(), excludeId)
                : t.jdbc().queryForObject(t.q(sql), Long.class, position.name());
        return n != null && n > 0;
    }

    @Override
    public void insert(BoardMember m) {
        t.jdbc().update(t.q("""
                INSERT INTO {s}.board_members (id, person_id, position, start_date, end_date, status, notes)
                VALUES (?::uuid, ?::uuid, ?::varchar, ?::date, ?::date, ?::varchar, ?::varchar)
                """), m.id(), m.personId(), m.position().name(), m.startDate(), m.endDate(), m.status().name(),
                m.notes());
    }

    @Override
    public boolean update(BoardMember m) {
        return t.jdbc().update(t.q("""
                UPDATE {s}.board_members SET person_id = ?::uuid, position = ?::varchar, start_date = ?::date,
                       end_date = ?::date, status = ?::varchar, notes = ?::varchar, updated_at = now()
                WHERE id = ?::uuid AND deleted_at IS NULL
                """), m.personId(), m.position().name(), m.startDate(), m.endDate(), m.status().name(), m.notes(),
                m.id()) > 0;
    }

    @Override
    public boolean softDelete(UUID id, UUID by, Instant at) {
        return t.jdbc().update(t.q("UPDATE {s}.board_members SET deleted_at = ?, deleted_by = ?, updated_at = now() "
                + "WHERE id = ? AND deleted_at IS NULL"), Jdbc.ts(at), by, id) > 0;
    }
}
