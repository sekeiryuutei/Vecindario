package com.codevam.vecindad.reservations.adapter.out;

import com.codevam.vecindad.reservations.application.port.out.ReservationPort;
import com.codevam.vecindad.reservations.domain.PaymentMethod;
import com.codevam.vecindad.reservations.domain.Reservation;
import com.codevam.vecindad.reservations.domain.ReservationStatus;
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
public class JdbcReservationAdapter implements ReservationPort {

    private static final String COLS = """
            id, common_area_id, unit_id, person_id, created_by, start_time, end_time,
            status, total_price, deposit_amount, payment_method, payment_reference,
            rejection_reason, cancellation_reason, notes, created_at, updated_at
            """;

    private static final RowMapper<Reservation> MAPPER = (rs, rowNum) -> new Reservation(
            Jdbc.uuid(rs, "id"),
            Jdbc.uuid(rs, "common_area_id"),
            Jdbc.uuid(rs, "unit_id"),
            Jdbc.uuid(rs, "person_id"),
            Jdbc.uuid(rs, "created_by"),
            Jdbc.instant(rs, "start_time"),
            Jdbc.instant(rs, "end_time"),
            ReservationStatus.valueOf(rs.getString("status")),
            rs.getBigDecimal("total_price"),
            rs.getBigDecimal("deposit_amount"),
            PaymentMethod.valueOf(rs.getString("payment_method")),
            rs.getString("payment_reference"),
            rs.getString("rejection_reason"),
            rs.getString("cancellation_reason"),
            rs.getString("notes"),
            Jdbc.instant(rs, "created_at"),
            Jdbc.instant(rs, "updated_at")
    );

    private final TenantJdbc t;

    public JdbcReservationAdapter(TenantJdbc t) {
        this.t = t;
    }

    @Override
    public Reservation insert(Reservation r) {
        UUID id = r.id() == null ? UUID.randomUUID() : r.id();
        String sql = """
                INSERT INTO {s}.reservations (
                    id, common_area_id, unit_id, person_id, created_by, start_time, end_time,
                    status, total_price, deposit_amount, payment_method, payment_reference,
                    notes
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        t.jdbc().update(t.q(sql),
                id, r.commonAreaId(), r.unitId(), r.personId(), r.createdBy(),
                Jdbc.ts(r.startTime()), Jdbc.ts(r.endTime()), r.status().name(),
                r.totalPrice(), r.depositAmount(), r.paymentMethod().name(),
                r.paymentReference(), r.notes());
        return findById(id).orElseThrow();
    }

    @Override
    public Optional<Reservation> findById(UUID id) {
        return t.jdbc().query(t.q("SELECT " + COLS + " FROM {s}.reservations WHERE id = ?"), MAPPER, id)
                .stream().findFirst();
    }

    @Override
    public List<Reservation> findOverlapping(UUID commonAreaId, Instant start, Instant end, UUID excludeId) {
        String sql = "SELECT " + COLS + " FROM {s}.reservations WHERE common_area_id = ? "
                + "AND status IN ('PENDING_PAYMENT', 'PENDING_APPROVAL', 'APPROVED') "
                + "AND start_time < ? AND end_time > ?"
                + (excludeId != null ? " AND id <> ?" : "");
        List<Object> args = new ArrayList<>();
        args.add(commonAreaId);
        args.add(Jdbc.ts(end));
        args.add(Jdbc.ts(start));
        if (excludeId != null) {
            args.add(excludeId);
        }
        return t.jdbc().query(t.q(sql), MAPPER, args.toArray());
    }

    @Override
    public int countActiveByUnit(UUID unitId) {
        Integer count = t.jdbc().queryForObject(
                t.q("SELECT count(*) FROM {s}.reservations WHERE unit_id = ? AND status IN ('PENDING_PAYMENT', 'PENDING_APPROVAL', 'APPROVED') AND end_time > now()"),
                Integer.class, unitId);
        return count != null ? count : 0;
    }

    @Override
    public List<Reservation> findByUnit(UUID unitId) {
        return t.jdbc().query(t.q("SELECT " + COLS + " FROM {s}.reservations WHERE unit_id = ? ORDER BY start_time DESC"),
                MAPPER, unitId);
    }

    @Override
    public List<Reservation> findByCommonAreaAndRange(UUID commonAreaId, Instant start, Instant end) {
        return t.jdbc().query(t.q("SELECT " + COLS + " FROM {s}.reservations WHERE common_area_id = ? AND status IN ('PENDING_PAYMENT', 'PENDING_APPROVAL', 'APPROVED') AND start_time < ? AND end_time > ? ORDER BY start_time ASC"),
                MAPPER, commonAreaId, Jdbc.ts(end), Jdbc.ts(start));
    }

    @Override
    public PageResult<Reservation> search(UUID commonAreaId, UUID unitId, ReservationStatus status,
                                          Instant from, Instant to, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (commonAreaId != null) {
            where.append(" AND common_area_id = ?");
            args.add(commonAreaId);
        }
        if (unitId != null) {
            where.append(" AND unit_id = ?");
            args.add(unitId);
        }
        if (status != null) {
            where.append(" AND status = ?");
            args.add(status.name());
        }
        if (from != null) {
            where.append(" AND start_time >= ?");
            args.add(Jdbc.ts(from));
        }
        if (to != null) {
            where.append(" AND start_time < ?");
            args.add(Jdbc.ts(to));
        }

        Long total = t.jdbc().queryForObject(t.q("SELECT count(*) FROM {s}.reservations" + where), Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add(page * size);
        List<Reservation> rows = t.jdbc().query(t.q("SELECT " + COLS + " FROM {s}.reservations" + where
                + " ORDER BY start_time DESC LIMIT ? OFFSET ?"), MAPPER, pageArgs.toArray());
        return PageResult.of(rows, page, size, total == null ? 0 : total);
    }

    @Override
    public boolean updateStatus(UUID id, ReservationStatus status, String rejectionReason, String cancellationReason) {
        return t.jdbc().update(t.q("""
                UPDATE {s}.reservations SET
                    status = ?,
                    rejection_reason = COALESCE(?, rejection_reason),
                    cancellation_reason = COALESCE(?, cancellation_reason),
                    updated_at = now()
                WHERE id = ?
                """), status.name(), rejectionReason, cancellationReason, id) > 0;
    }

    @Override
    public boolean markPaid(UUID id, ReservationStatus status, String paymentReference) {
        return t.jdbc().update(t.q("""
                UPDATE {s}.reservations SET
                    status = ?,
                    payment_method = 'WOMPI',
                    payment_reference = ?,
                    updated_at = now()
                WHERE id = ? AND status = 'PENDING_PAYMENT'
                """), status.name(), paymentReference, id) > 0;
    }

    @Override
    public boolean isUnitBlockedByDebt(UUID unitId) {
        String sql = """
                SELECT EXISTS (
                    SELECT 1 FROM {s}.billing_settings bs
                    WHERE bs.block_reservations_when_overdue = true
                      AND EXISTS (
                          SELECT 1 FROM {s}.charges c
                          WHERE c.unit_id = ?
                            AND c.voided_at IS NULL
                            AND (c.amount + c.adjusted_amount > c.paid_amount)
                            AND c.due_date < CURRENT_DATE - (bs.overdue_days_for_block || ' days')::interval
                      )
                )
                """;
        Boolean blocked = t.jdbc().queryForObject(t.q(sql), Boolean.class, unitId);
        return Boolean.TRUE.equals(blocked);
    }
}

