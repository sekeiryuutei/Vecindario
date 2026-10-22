package com.codevam.vecindad.staff.adapter.out;

import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.persistence.Jdbc;
import com.codevam.vecindad.shared.tenancy.TenantJdbc;
import com.codevam.vecindad.staff.application.port.out.StaffPort;
import com.codevam.vecindad.staff.domain.StaffMember;
import com.codevam.vecindad.staff.domain.StaffStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcStaffAdapter implements StaffPort {
    private static final String SELECT = """
            SELECT id, full_name, document_type, document_number, position, area, phone, email, shift,
                   start_date, end_date, status, notes, created_at, updated_at
            FROM {s}.staff_members WHERE deleted_at IS NULL
            """;

    private static final RowMapper<StaffMember> MAPPER = (rs, i) -> new StaffMember(
            Jdbc.uuid(rs, "id"), rs.getString("full_name"), rs.getString("document_type"),
            rs.getString("document_number"), rs.getString("position"), rs.getString("area"),
            rs.getString("phone"), rs.getString("email"), rs.getString("shift"),
            rs.getObject("start_date", LocalDate.class), rs.getObject("end_date", LocalDate.class),
            StaffStatus.valueOf(rs.getString("status")), rs.getString("notes"),
            Jdbc.instant(rs, "created_at"), Jdbc.instant(rs, "updated_at"));

    private final TenantJdbc t;

    public JdbcStaffAdapter(TenantJdbc t) {
        this.t = t;
    }

    @Override
    public Optional<StaffMember> findById(UUID id) {
        return t.jdbc().query(t.q(SELECT + " AND id = ?"), MAPPER, id).stream().findFirst();
    }

    @Override
    public PageResult<StaffMember> search(String q, StaffStatus status, int page, int size) {
        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        if (q != null) {
            where.append(" AND (lower(full_name) LIKE ? OR lower(document_number) LIKE ? "
                    + "OR lower(position) LIKE ? OR lower(coalesce(area, '')) LIKE ?)");
            String like = "%" + q.toLowerCase().trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (status != null) {
            where.append(" AND status = ?");
            args.add(status.name());
        }
        Long total = t.jdbc().queryForObject(t.q("SELECT count(*) FROM {s}.staff_members WHERE deleted_at IS NULL"
                + where), Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((long) page * size);
        List<StaffMember> rows = t.jdbc().query(t.q(SELECT + where
                        + " ORDER BY status, full_name LIMIT ? OFFSET ?"), MAPPER, pageArgs.toArray());
        return PageResult.of(rows, page, size, total == null ? 0 : total);
    }

    @Override
    public boolean documentExists(String documentType, String documentNumber, UUID excludeId) {
        String sql = "SELECT count(*) FROM {s}.staff_members WHERE deleted_at IS NULL "
                + "AND document_type = ? AND document_number = ?" + (excludeId != null ? " AND id <> ?" : "");
        Long n = excludeId != null
                ? t.jdbc().queryForObject(t.q(sql), Long.class, documentType, documentNumber, excludeId)
                : t.jdbc().queryForObject(t.q(sql), Long.class, documentType, documentNumber);
        return n != null && n > 0;
    }

    @Override
    public void insert(StaffMember m) {
        t.jdbc().update(t.q("""
                INSERT INTO {s}.staff_members (id, full_name, document_type, document_number, position, area, phone,
                       email, shift, start_date, end_date, status, notes)
                VALUES (?::uuid, ?::varchar, ?::varchar, ?::varchar, ?::varchar, ?::varchar, ?::varchar, ?::varchar,
                        ?::varchar, ?::date, ?::date, ?::varchar, ?::varchar)
                """), m.id(), m.fullName(), m.documentType(), m.documentNumber(), m.position(), m.area(), m.phone(),
                m.email(), m.shift(), m.startDate(), m.endDate(), m.status().name(), m.notes());
    }

    @Override
    public boolean update(StaffMember m) {
        return t.jdbc().update(t.q("""
                UPDATE {s}.staff_members SET full_name = ?::varchar, document_type = ?::varchar,
                       document_number = ?::varchar, position = ?::varchar, area = ?::varchar, phone = ?::varchar,
                       email = ?::varchar, shift = ?::varchar, start_date = ?::date, end_date = ?::date,
                       status = ?::varchar, notes = ?::varchar, updated_at = now()
                WHERE id = ?::uuid AND deleted_at IS NULL
                """), m.fullName(), m.documentType(), m.documentNumber(), m.position(), m.area(), m.phone(),
                m.email(), m.shift(), m.startDate(), m.endDate(), m.status().name(), m.notes(), m.id()) > 0;
    }

    @Override
    public boolean softDelete(UUID id, UUID by, Instant at) {
        return t.jdbc().update(t.q("UPDATE {s}.staff_members SET deleted_at = ?, deleted_by = ?, updated_at = now() "
                + "WHERE id = ? AND deleted_at IS NULL"), Jdbc.ts(at), by, id) > 0;
    }
}
