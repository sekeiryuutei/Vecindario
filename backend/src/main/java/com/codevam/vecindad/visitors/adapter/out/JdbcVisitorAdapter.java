package com.codevam.vecindad.visitors.adapter.out;

import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.persistence.Jdbc;
import com.codevam.vecindad.shared.tenancy.TenantJdbc;
import com.codevam.vecindad.visitors.application.port.out.VisitorPort;
import com.codevam.vecindad.visitors.domain.Invitation;
import com.codevam.vecindad.visitors.domain.Visit;
import com.codevam.vecindad.visitors.domain.VisitStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class JdbcVisitorAdapter implements VisitorPort {
    private static final String INV_SELECT = """
            SELECT i.id, i.unit_id, u.identifier AS unit_identifier, i.visitor_name, i.document_number, i.phone, i.plate,
                   i.people_count, i.valid_from, i.valid_to, i.max_entries, i.used_count, i.status, i.notes, i.created_at
            FROM {s}.visitor_invitations i JOIN {s}.property_units u ON u.id = i.unit_id
            """;
    private static final String VISIT_SELECT = """
            SELECT v.id, v.invitation_id, v.unit_id, u.identifier AS unit_identifier, v.visitor_name, v.document_number,
                   v.phone, v.plate, v.people_count, v.source, v.status, v.requested_at, v.decided_at, v.decision_note,
                   v.entry_time, v.exit_time, v.note
            FROM {s}.visits v JOIN {s}.property_units u ON u.id = v.unit_id
            """;

    private static final RowMapper<Invitation> INVITATION = (rs, i) -> new Invitation(Jdbc.uuid(rs, "id"), Jdbc.uuid(rs, "unit_id"),
            rs.getString("unit_identifier"), rs.getString("visitor_name"), rs.getString("document_number"), rs.getString("phone"),
            rs.getString("plate"), rs.getInt("people_count"), Jdbc.instant(rs, "valid_from"), Jdbc.instant(rs, "valid_to"),
            rs.getInt("max_entries"), rs.getInt("used_count"), rs.getString("status"), rs.getString("notes"), Jdbc.instant(rs, "created_at"));

    private static final RowMapper<Visit> VISIT = (rs, i) -> new Visit(Jdbc.uuid(rs, "id"), Jdbc.uuid(rs, "invitation_id"),
            Jdbc.uuid(rs, "unit_id"), rs.getString("unit_identifier"), rs.getString("visitor_name"), rs.getString("document_number"),
            rs.getString("phone"), rs.getString("plate"), rs.getInt("people_count"), rs.getString("source"),
            VisitStatus.valueOf(rs.getString("status")), Jdbc.instant(rs, "requested_at"), Jdbc.instant(rs, "decided_at"),
            rs.getString("decision_note"), Jdbc.instant(rs, "entry_time"), Jdbc.instant(rs, "exit_time"), rs.getString("note"));

    private final TenantJdbc t;

    public JdbcVisitorAdapter(TenantJdbc t) {
        this.t = t;
    }

    @Override
    public Invitation insertInvitation(Invitation inv, String qrHash, UUID createdBy) {
        t.jdbc().update(t.q("INSERT INTO {s}.visitor_invitations (id, unit_id, created_by, visitor_name, document_number, phone, plate, "
                        + "people_count, valid_from, valid_to, max_entries, qr_hash, notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"),
                inv.id(), inv.unitId(), createdBy, inv.visitorName(), inv.documentNumber(), inv.phone(), inv.plate(), inv.peopleCount(),
                Jdbc.ts(inv.validFrom()), Jdbc.ts(inv.validTo()), inv.maxEntries(), qrHash, inv.notes());
        return findInvitation(inv.id()).orElseThrow();
    }

    @Override
    public Optional<Invitation> findInvitation(UUID id) {
        return t.jdbc().query(t.q(INV_SELECT + " WHERE i.id = ?"), INVITATION, id).stream().findFirst();
    }

    @Override
    public Optional<Invitation> findInvitationByQrHash(String qrHash) {
        return t.jdbc().query(t.q(INV_SELECT + " WHERE i.qr_hash = ?"), INVITATION, qrHash).stream().findFirst();
    }

    @Override
    public PageResult<Invitation> searchInvitations(List<UUID> unitIds, boolean validNowOnly, int page, int size) {
        if (unitIds != null && unitIds.isEmpty()) return PageResult.of(List.of(), page, size, 0);
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (unitIds != null) {
            where.append(" AND i.unit_id IN (").append(placeholders(unitIds)).append(")");
            args.addAll(unitIds);
        }
        if (validNowOnly) {
            where.append(" AND i.status = 'ACTIVE' AND now() BETWEEN i.valid_from AND i.valid_to AND i.used_count < i.max_entries");
        }
        Long total = t.jdbc().queryForObject(t.q("SELECT count(*) FROM {s}.visitor_invitations i" + where), Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add(page * size);
        List<Invitation> rows = t.jdbc().query(t.q(INV_SELECT + where + " ORDER BY i.valid_to DESC, i.id LIMIT ? OFFSET ?"), INVITATION, pageArgs.toArray());
        return PageResult.of(rows, page, size, total == null ? 0 : total);
    }

    @Override
    public boolean cancelInvitation(UUID id) {
        return t.jdbc().update(t.q("UPDATE {s}.visitor_invitations SET status = 'CANCELLED', cancelled_at = now() "
                + "WHERE id = ? AND status = 'ACTIVE'"), id) > 0;
    }

    @Override
    public boolean replaceQr(UUID id, String newQrHash) {
        return t.jdbc().update(t.q("UPDATE {s}.visitor_invitations SET qr_hash = ? WHERE id = ? AND status = 'ACTIVE'"), newQrHash, id) > 0;
    }

    @Override
    public Optional<UUID> checkInByQr(String qrHash, UUID guard) {
        // Una sola sentencia: consumir el uso de la invitación y crear la visita es atómico aunque lleguen lecturas simultáneas del mismo QR.
        return t.jdbc().query(t.q("""
                WITH inv AS (
                    UPDATE {s}.visitor_invitations SET used_count = used_count + 1
                    WHERE qr_hash = ? AND status = 'ACTIVE' AND used_count < max_entries
                      AND now() >= valid_from AND now() <= valid_to
                    RETURNING id, unit_id, visitor_name, document_number, phone, plate, people_count)
                INSERT INTO {s}.visits (id, invitation_id, unit_id, visitor_name, document_number, phone, plate, people_count,
                                        source, status, requested_by, entry_time, entry_guard)
                SELECT ?::uuid, inv.id, inv.unit_id, inv.visitor_name, inv.document_number, inv.phone, inv.plate, inv.people_count,
                       'INVITATION', 'INSIDE', ?::uuid, now(), ?::uuid FROM inv
                RETURNING id
                """), (rs, i) -> Jdbc.uuid(rs, "id"), qrHash, UUID.randomUUID(), guard, guard).stream().findFirst();
    }

    @Override
    public Visit insertWalkIn(Visit v, UUID guard) {
        t.jdbc().update(t.q("INSERT INTO {s}.visits (id, unit_id, visitor_name, document_number, phone, plate, people_count, source, "
                        + "status, requested_by, note) VALUES (?, ?, ?, ?, ?, ?, ?, 'WALK_IN', 'PENDING_AUTH', ?, ?)"),
                v.id(), v.unitId(), v.visitorName(), v.documentNumber(), v.phone(), v.plate(), v.peopleCount(), guard, v.note());
        return findVisit(v.id()).orElseThrow();
    }

    @Override
    public Optional<Visit> findVisit(UUID id) {
        return t.jdbc().query(t.q(VISIT_SELECT + " WHERE v.id = ?"), VISIT, id).stream().findFirst();
    }

    @Override
    public PageResult<Visit> searchVisits(List<UUID> unitIds, VisitStatus status, int page, int size) {
        if (unitIds != null && unitIds.isEmpty()) return PageResult.of(List.of(), page, size, 0);
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (unitIds != null) {
            where.append(" AND v.unit_id IN (").append(placeholders(unitIds)).append(")");
            args.addAll(unitIds);
        }
        if (status != null) {
            where.append(" AND v.status = ?");
            args.add(status.name());
        }
        Long total = t.jdbc().queryForObject(t.q("SELECT count(*) FROM {s}.visits v" + where), Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add(page * size);
        List<Visit> rows = t.jdbc().query(t.q(VISIT_SELECT + where + " ORDER BY v.requested_at DESC, v.id LIMIT ? OFFSET ?"), VISIT, pageArgs.toArray());
        return PageResult.of(rows, page, size, total == null ? 0 : total);
    }

    @Override
    public boolean decide(UUID visitId, boolean authorize, UUID by, String note) {
        return t.jdbc().update(t.q("UPDATE {s}.visits SET status = ?, decided_by = ?, decided_at = now(), decision_note = ? "
                + "WHERE id = ? AND status = 'PENDING_AUTH'"), authorize ? "AUTHORIZED" : "REJECTED", by, note, visitId) > 0;
    }

    @Override
    public boolean checkInAuthorized(UUID visitId, UUID guard) {
        return t.jdbc().update(t.q("UPDATE {s}.visits SET status = 'INSIDE', entry_time = now(), entry_guard = ? "
                + "WHERE id = ? AND status = 'AUTHORIZED'"), guard, visitId) > 0;
    }

    @Override
    public boolean checkOut(UUID visitId, UUID guard) {
        return t.jdbc().update(t.q("UPDATE {s}.visits SET status = 'LEFT', exit_time = now(), exit_guard = ? "
                + "WHERE id = ? AND status = 'INSIDE'"), guard, visitId) > 0;
    }

    @Override
    public void saveAlert(String type, String plate, UUID unitId, String message, UUID guard) {
        t.jdbc().update(t.q("INSERT INTO {s}.security_alerts (id, alert_type, plate, unit_id, message, guard_user_id) VALUES (?, ?, ?, ?, ?, ?)"),
                UUID.randomUUID(), type, plate, unitId, message, guard);
    }

    private static String placeholders(List<UUID> ids) {
        return ids.stream().map(x -> "?").collect(Collectors.joining(","));
    }
}
