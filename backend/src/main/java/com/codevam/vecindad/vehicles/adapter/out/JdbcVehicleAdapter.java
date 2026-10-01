package com.codevam.vecindad.vehicles.adapter.out;

import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.persistence.Jdbc;
import com.codevam.vecindad.shared.tenancy.TenantJdbc;
import com.codevam.vecindad.vehicles.application.port.out.VehiclePort;
import com.codevam.vecindad.vehicles.domain.Presence;
import com.codevam.vecindad.vehicles.domain.Vehicle;
import com.codevam.vecindad.vehicles.domain.VehicleStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcVehicleAdapter implements VehiclePort {
    private static final String SELECT = """
            SELECT v.id, v.vehicle_type_code, v.plate, v.brand, v.model, v.color, v.model_year, v.owner_person_id,
                   v.unit_id, u.identifier AS unit_identifier, v.parking_space_id, v.status, v.presence, v.is_primary,
                   v.created_at, v.updated_at
            FROM {s}.vehicles v JOIN {s}.property_units u ON u.id = v.unit_id
            WHERE v.deleted_at IS NULL
            """;

    private static final RowMapper<Vehicle> MAPPER = (rs, i) -> new Vehicle(Jdbc.uuid(rs, "id"),
            rs.getString("vehicle_type_code"), rs.getString("plate"), rs.getString("brand"), rs.getString("model"),
            rs.getString("color"), rs.getObject("model_year", Integer.class), Jdbc.uuid(rs, "owner_person_id"),
            Jdbc.uuid(rs, "unit_id"), rs.getString("unit_identifier"), Jdbc.uuid(rs, "parking_space_id"),
            VehicleStatus.valueOf(rs.getString("status")), Presence.valueOf(rs.getString("presence")),
            rs.getBoolean("is_primary"), Jdbc.instant(rs, "created_at"), Jdbc.instant(rs, "updated_at"));

    private final TenantJdbc t;

    public JdbcVehicleAdapter(TenantJdbc t) {
        this.t = t;
    }

    @Override
    public Optional<Vehicle> findById(UUID id) {
        return t.jdbc().query(t.q(SELECT + " AND v.id = ?"), MAPPER, id).stream().findFirst();
    }

    @Override
    public Optional<Vehicle> findByPlate(String plate) {
        return t.jdbc().query(t.q(SELECT + " AND upper(v.plate) = ?"), MAPPER, plate).stream().findFirst();
    }

    @Override
    public PageResult<Vehicle> search(String plate, UUID unitId, Presence presence, int page, int size) {
        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        if (plate != null) {
            where.append(" AND upper(v.plate) LIKE ?");
            args.add("%" + plate.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        }
        if (unitId != null) {
            where.append(" AND v.unit_id = ?");
            args.add(unitId);
        }
        if (presence != null) {
            where.append(" AND v.presence = ?");
            args.add(presence.name());
        }
        Long total = t.jdbc().queryForObject(t.q("SELECT count(*) FROM ({s}.vehicles v JOIN {s}.property_units u ON u.id = v.unit_id) "
                + "WHERE v.deleted_at IS NULL" + where), Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add(page * size);
        List<Vehicle> rows = t.jdbc().query(t.q(SELECT + where + " ORDER BY u.identifier, v.plate, v.id LIMIT ? OFFSET ?"),
                MAPPER, pageArgs.toArray());
        return PageResult.of(rows, page, size, total == null ? 0 : total);
    }

    @Override
    public List<Vehicle> inside() {
        return t.jdbc().query(t.q(SELECT + " AND v.presence = 'INSIDE' ORDER BY u.identifier, v.plate"), MAPPER);
    }

    @Override
    public int countActive(UUID unitId, String typeCode) {
        Integer n = t.jdbc().queryForObject(t.q("SELECT count(*) FROM {s}.vehicles WHERE unit_id = ? AND vehicle_type_code = ? "
                + "AND deleted_at IS NULL"), Integer.class, unitId, typeCode);
        return n == null ? 0 : n;
    }

    @Override
    public boolean insertIfBelowLimit(Vehicle v, int limit) {
        return t.jdbc().update(t.q("""
                INSERT INTO {s}.vehicles (id, vehicle_type_code, plate, brand, model, color, model_year, owner_person_id,
                                          unit_id, parking_space_id, status, is_primary)
                SELECT ?::uuid, ?::varchar, ?::varchar, ?::varchar, ?::varchar, ?::varchar, ?::int, ?::uuid,
                       ?::uuid, ?::uuid, ?::varchar, ?::boolean
                WHERE (SELECT count(*) FROM {s}.vehicles WHERE unit_id = ?::uuid AND vehicle_type_code = ?::varchar
                       AND deleted_at IS NULL) < ?::int
                """), v.id(), v.typeCode(), v.plate(), v.brand(), v.model(), v.color(), v.modelYear(), v.ownerPersonId(),
                v.unitId(), v.parkingSpaceId(), v.status().name(), v.primary(), v.unitId(), v.typeCode(), limit) > 0;
    }

    @Override
    public boolean updateIfOutside(Vehicle v) {
        return t.jdbc().update(t.q("""
                UPDATE {s}.vehicles SET vehicle_type_code = ?, plate = ?, brand = ?, model = ?, color = ?, model_year = ?,
                       owner_person_id = ?, unit_id = ?, parking_space_id = ?, status = ?, updated_at = now()
                WHERE id = ? AND presence = 'OUTSIDE' AND deleted_at IS NULL
                """), v.typeCode(), v.plate(), v.brand(), v.model(), v.color(), v.modelYear(), v.ownerPersonId(), v.unitId(),
                v.parkingSpaceId(), v.status().name(), v.id()) > 0;
    }

    @Override
    public boolean softDeleteIfOutside(UUID id, UUID by, Instant at) {
        return t.jdbc().update(t.q("UPDATE {s}.vehicles SET deleted_at = ?, deleted_by = ?, updated_at = now() "
                + "WHERE id = ? AND presence = 'OUTSIDE' AND deleted_at IS NULL"), Jdbc.ts(at), by, id) > 0;
    }
}
