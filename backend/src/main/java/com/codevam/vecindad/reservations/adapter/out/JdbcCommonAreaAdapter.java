package com.codevam.vecindad.reservations.adapter.out;

import com.codevam.vecindad.reservations.application.port.out.CommonAreaPort;
import com.codevam.vecindad.reservations.domain.CommonArea;
import com.codevam.vecindad.shared.persistence.Jdbc;
import com.codevam.vecindad.shared.tenancy.TenantJdbc;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Time;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcCommonAreaAdapter implements CommonAreaPort {

    private static final String COLS = """
            id, name, description, capacity, requires_payment, price_per_slot, deposit_amount,
            requires_approval, opening_time, closing_time, slot_duration_minutes, max_slots_per_reservation,
            max_active_reservations_per_unit, min_advance_hours, max_advance_days, cancellation_deadline_hours,
            is_active, created_at, updated_at
            """;

    private static final RowMapper<CommonArea> MAPPER = (rs, rowNum) -> new CommonArea(
            Jdbc.uuid(rs, "id"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getInt("capacity"),
            rs.getBoolean("requires_payment"),
            rs.getBigDecimal("price_per_slot"),
            rs.getBigDecimal("deposit_amount"),
            rs.getBoolean("requires_approval"),
            rs.getTime("opening_time").toLocalTime(),
            rs.getTime("closing_time").toLocalTime(),
            rs.getInt("slot_duration_minutes"),
            rs.getInt("max_slots_per_reservation"),
            rs.getInt("max_active_reservations_per_unit"),
            rs.getInt("min_advance_hours"),
            rs.getInt("max_advance_days"),
            rs.getInt("cancellation_deadline_hours"),
            rs.getBoolean("is_active"),
            Jdbc.instant(rs, "created_at"),
            Jdbc.instant(rs, "updated_at")
    );

    private final TenantJdbc t;

    public JdbcCommonAreaAdapter(TenantJdbc t) {
        this.t = t;
    }

    @Override
    public CommonArea insert(CommonArea a) {
        UUID id = a.id() == null ? UUID.randomUUID() : a.id();
        String sql = """
                INSERT INTO {s}.common_areas (
                    id, name, description, capacity, requires_payment, price_per_slot, deposit_amount,
                    requires_approval, opening_time, closing_time, slot_duration_minutes, max_slots_per_reservation,
                    max_active_reservations_per_unit, min_advance_hours, max_advance_days, cancellation_deadline_hours,
                    is_active
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        t.jdbc().update(t.q(sql),
                id, a.name(), a.description(), a.capacity(), a.requiresPayment(),
                a.pricePerSlot(), a.depositAmount(), a.requiresApproval(),
                Time.valueOf(a.openingTime()), Time.valueOf(a.closingTime()),
                a.slotDurationMinutes(), a.maxSlotsPerReservation(), a.maxActiveReservationsPerUnit(),
                a.minAdvanceHours(), a.maxAdvanceDays(), a.cancellationDeadlineHours(), a.isActive());
        return findById(id).orElseThrow();
    }

    @Override
    public Optional<CommonArea> findById(UUID id) {
        return t.jdbc().query(t.q("SELECT " + COLS + " FROM {s}.common_areas WHERE id = ?"), MAPPER, id)
                .stream().findFirst();
    }

    @Override
    public List<CommonArea> list(boolean onlyActive) {
        String sql = "SELECT " + COLS + " FROM {s}.common_areas " + (onlyActive ? "WHERE is_active = true " : "") + "ORDER BY name ASC";
        return t.jdbc().query(t.q(sql), MAPPER);
    }

    @Override
    public boolean update(CommonArea a) {
        String sql = """
                UPDATE {s}.common_areas SET
                    name = ?, description = ?, capacity = ?, requires_payment = ?, price_per_slot = ?,
                    deposit_amount = ?, requires_approval = ?, opening_time = ?, closing_time = ?,
                    slot_duration_minutes = ?, max_slots_per_reservation = ?, max_active_reservations_per_unit = ?,
                    min_advance_hours = ?, max_advance_days = ?, cancellation_deadline_hours = ?, is_active = ?,
                    updated_at = now()
                WHERE id = ?
                """;
        return t.jdbc().update(t.q(sql),
                a.name(), a.description(), a.capacity(), a.requiresPayment(),
                a.pricePerSlot(), a.depositAmount(), a.requiresApproval(),
                Time.valueOf(a.openingTime()), Time.valueOf(a.closingTime()),
                a.slotDurationMinutes(), a.maxSlotsPerReservation(), a.maxActiveReservationsPerUnit(),
                a.minAdvanceHours(), a.maxAdvanceDays(), a.cancellationDeadlineHours(), a.isActive(),
                a.id()) > 0;
    }

    @Override
    public boolean delete(UUID id) {
        return t.jdbc().update(t.q("UPDATE {s}.common_areas SET is_active = false, updated_at = now() WHERE id = ?"), id) > 0;
    }
}

