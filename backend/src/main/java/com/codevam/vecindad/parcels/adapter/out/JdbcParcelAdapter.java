package com.codevam.vecindad.parcels.adapter.out;

import com.codevam.vecindad.parcels.application.port.out.ParcelPort;
import com.codevam.vecindad.parcels.domain.Parcel;
import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.persistence.Jdbc;
import com.codevam.vecindad.shared.tenancy.TenantJdbc;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class JdbcParcelAdapter implements ParcelPort {
    private static final String SELECT = """
            SELECT p.id, p.unit_id, u.identifier AS unit_identifier, p.recipient_name, p.carrier, p.tracking_number, p.description,
                   p.status, p.received_at, p.notified_at, p.delivered_at, p.delivered_to, p.returned_at, p.note
            FROM {s}.packages p JOIN {s}.property_units u ON u.id = p.unit_id
            """;

    private static final RowMapper<Parcel> MAPPER = (rs, i) -> new Parcel(Jdbc.uuid(rs, "id"), Jdbc.uuid(rs, "unit_id"),
            rs.getString("unit_identifier"), rs.getString("recipient_name"), rs.getString("carrier"), rs.getString("tracking_number"),
            rs.getString("description"), rs.getString("status"), Jdbc.instant(rs, "received_at"), Jdbc.instant(rs, "notified_at"),
            Jdbc.instant(rs, "delivered_at"), rs.getString("delivered_to"), Jdbc.instant(rs, "returned_at"), rs.getString("note"));

    private final TenantJdbc t;

    public JdbcParcelAdapter(TenantJdbc t) {
        this.t = t;
    }

    @Override
    public Parcel insert(Parcel p, UUID receivedBy) {
        t.jdbc().update(t.q("INSERT INTO {s}.packages (id, unit_id, recipient_name, carrier, tracking_number, description, received_by) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)"), p.id(), p.unitId(), p.recipientName(), p.carrier(), p.trackingNumber(), p.description(), receivedBy);
        return findById(p.id()).orElseThrow();
    }

    @Override
    public Optional<Parcel> findById(UUID id) {
        return t.jdbc().query(t.q(SELECT + " WHERE p.id = ?"), MAPPER, id).stream().findFirst();
    }

    @Override
    public PageResult<Parcel> search(List<UUID> unitIds, String status, String q, int page, int size) {
        if (unitIds != null && unitIds.isEmpty()) return PageResult.of(List.of(), page, size, 0);
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (unitIds != null) {
            where.append(" AND p.unit_id IN (").append(unitIds.stream().map(x -> "?").collect(Collectors.joining(","))).append(")");
            args.addAll(unitIds);
        }
        if (status != null) { where.append(" AND p.status = ?"); args.add(status); }
        if (q != null) {
            String like = "%" + q.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            where.append(" AND (lower(p.recipient_name) LIKE ? OR lower(coalesce(p.tracking_number,'')) LIKE ? OR lower(coalesce(p.carrier,'')) LIKE ?)");
            args.add(like); args.add(like); args.add(like);
        }
        Long total = t.jdbc().queryForObject(t.q("SELECT count(*) FROM {s}.packages p" + where), Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add(page * size);
        List<Parcel> rows = t.jdbc().query(t.q(SELECT + where + " ORDER BY p.received_at DESC, p.id LIMIT ? OFFSET ?"), MAPPER, pageArgs.toArray());
        return PageResult.of(rows, page, size, total == null ? 0 : total);
    }

    @Override
    public boolean markDelivered(UUID id, String deliveredTo, UUID by) {
        return t.jdbc().update(t.q("UPDATE {s}.packages SET status = 'DELIVERED', delivered_at = now(), delivered_by = ?, delivered_to = ? "
                + "WHERE id = ? AND status IN ('RECEIVED','NOTIFIED')"), by, deliveredTo, id) > 0;
    }

    @Override
    public boolean markReturned(UUID id, String note, UUID by) {
        return t.jdbc().update(t.q("UPDATE {s}.packages SET status = 'RETURNED', returned_at = now(), returned_by = ?, note = ? "
                + "WHERE id = ? AND status IN ('RECEIVED','NOTIFIED')"), by, note, id) > 0;
    }
}
