package com.codevam.vecindad.vehicles.adapter.out;

import com.codevam.vecindad.shared.persistence.Jdbc;
import com.codevam.vecindad.shared.tenancy.TenantJdbc;
import com.codevam.vecindad.vehicles.application.port.out.VehicleCatalogPort;
import com.codevam.vecindad.vehicles.domain.ParkingSpace;
import com.codevam.vecindad.vehicles.domain.VehicleLimit;
import com.codevam.vecindad.vehicles.domain.VehicleType;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcVehicleCatalogAdapter implements VehicleCatalogPort {
    private static final RowMapper<VehicleType> TYPE = (rs, i) -> new VehicleType(rs.getString("code"), rs.getString("name"),
            rs.getBoolean("active"), rs.getBoolean("requires_plate"));
    private static final RowMapper<ParkingSpace> PARKING = (rs, i) -> new ParkingSpace(Jdbc.uuid(rs, "id"), rs.getString("code"),
            rs.getString("kind"), Jdbc.uuid(rs, "unit_id"), rs.getString("status"));

    private final TenantJdbc t;

    public JdbcVehicleCatalogAdapter(TenantJdbc t) {
        this.t = t;
    }

    @Override
    public List<VehicleType> types() {
        return t.jdbc().query(t.q("SELECT code, name, active, requires_plate FROM {s}.vehicle_types ORDER BY name"), TYPE);
    }

    @Override
    public Optional<VehicleType> findType(String code) {
        return t.jdbc().query(t.q("SELECT code, name, active, requires_plate FROM {s}.vehicle_types WHERE code = ?"), TYPE, code)
                .stream().findFirst();
    }

    @Override
    public void upsertType(VehicleType v) {
        t.jdbc().update(t.q("INSERT INTO {s}.vehicle_types (code, name, active, requires_plate) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, active = EXCLUDED.active, "
                + "requires_plate = EXCLUDED.requires_plate"), v.code(), v.name(), v.active(), v.requiresPlate());
    }

    @Override
    public List<VehicleLimit> defaultLimits() {
        return t.jdbc().query(t.q("SELECT vehicle_type_code, max_per_unit FROM {s}.vehicle_type_limits ORDER BY vehicle_type_code"),
                (rs, i) -> new VehicleLimit(rs.getString("vehicle_type_code"), rs.getInt("max_per_unit")));
    }

    @Override
    public void setDefaultLimit(String typeCode, int max) {
        t.jdbc().update(t.q("INSERT INTO {s}.vehicle_type_limits (vehicle_type_code, max_per_unit) VALUES (?, ?) "
                + "ON CONFLICT (vehicle_type_code) DO UPDATE SET max_per_unit = EXCLUDED.max_per_unit"), typeCode, max);
    }

    @Override
    public void setUnitLimit(UUID unitId, String typeCode, int max) {
        t.jdbc().update(t.q("INSERT INTO {s}.unit_vehicle_limits (unit_id, vehicle_type_code, max_count) VALUES (?, ?, ?) "
                + "ON CONFLICT (unit_id, vehicle_type_code) DO UPDATE SET max_count = EXCLUDED.max_count"), unitId, typeCode, max);
    }

    @Override
    public Integer effectiveLimit(UUID unitId, String typeCode) {
        List<Integer> r = t.jdbc().query(t.q("""
                SELECT COALESCE(
                    (SELECT max_count FROM {s}.unit_vehicle_limits WHERE unit_id = ? AND vehicle_type_code = ?),
                    (SELECT max_per_unit FROM {s}.vehicle_type_limits WHERE vehicle_type_code = ?)) AS lim
                """), (rs, i) -> rs.getObject("lim", Integer.class), unitId, typeCode, typeCode);
        return r.isEmpty() ? null : r.get(0);
    }

    @Override
    public ParkingSpace insertParking(ParkingSpace p) {
        t.jdbc().update(t.q("INSERT INTO {s}.parking_spaces (id, code, kind, unit_id, status) VALUES (?, ?, ?, ?, ?)"),
                p.id(), p.code(), p.kind(), p.unitId(), p.status());
        return findParking(p.id()).orElseThrow();
    }

    @Override
    public List<ParkingSpace> parkingSpaces() {
        return t.jdbc().query(t.q("SELECT id, code, kind, unit_id, status FROM {s}.parking_spaces WHERE deleted_at IS NULL ORDER BY code"), PARKING);
    }

    @Override
    public Optional<ParkingSpace> findParking(UUID id) {
        return t.jdbc().query(t.q("SELECT id, code, kind, unit_id, status FROM {s}.parking_spaces WHERE id = ? AND deleted_at IS NULL"),
                PARKING, id).stream().findFirst();
    }
}
