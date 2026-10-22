package com.codevam.vecindad.pets.adapter.out;

import com.codevam.vecindad.pets.application.port.out.PetPort;
import com.codevam.vecindad.pets.domain.Pet;
import com.codevam.vecindad.pets.domain.PetStatus;
import com.codevam.vecindad.pets.domain.Species;
import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.persistence.Jdbc;
import com.codevam.vecindad.shared.tenancy.TenantJdbc;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcPetAdapter implements PetPort {
    private static final String SELECT = """
            SELECT p.id, p.unit_id, u.identifier AS unit_identifier, p.name, p.species, p.breed, p.color,
                   p.birth_date, p.weight_kg, p.microchip, p.vaccinated, p.vaccine_expires_on, p.notes, p.status,
                   p.created_at, p.updated_at
            FROM {s}.pets p JOIN {s}.property_units u ON u.id = p.unit_id
            WHERE p.deleted_at IS NULL
            """;

    private static final RowMapper<Pet> MAPPER = (rs, i) -> new Pet(Jdbc.uuid(rs, "id"),
            Jdbc.uuid(rs, "unit_id"), rs.getString("unit_identifier"), rs.getString("name"),
            Species.valueOf(rs.getString("species")), rs.getString("breed"), rs.getString("color"),
            rs.getObject("birth_date", LocalDate.class), rs.getBigDecimal("weight_kg"), rs.getString("microchip"),
            rs.getBoolean("vaccinated"), rs.getObject("vaccine_expires_on", LocalDate.class), rs.getString("notes"),
            PetStatus.valueOf(rs.getString("status")), Jdbc.instant(rs, "created_at"), Jdbc.instant(rs, "updated_at"));

    private final TenantJdbc t;

    public JdbcPetAdapter(TenantJdbc t) {
        this.t = t;
    }

    @Override
    public Optional<Pet> findById(UUID id) {
        return t.jdbc().query(t.q(SELECT + " AND p.id = ?"), MAPPER, id).stream().findFirst();
    }

    @Override
    public Optional<Pet> findByMicrochip(String microchip) {
        return t.jdbc().query(t.q(SELECT + " AND upper(p.microchip) = ?"), MAPPER, microchip).stream().findFirst();
    }

    @Override
    public PageResult<Pet> search(String q, UUID unitId, Species species, PetStatus status, int page, int size) {
        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        if (q != null) {
            where.append(" AND (p.name ILIKE ? OR p.microchip ILIKE ?)");
            String pattern = like(q);
            args.add(pattern);
            args.add(pattern);
        }
        if (unitId != null) {
            where.append(" AND p.unit_id = ?");
            args.add(unitId);
        }
        if (species != null) {
            where.append(" AND p.species = ?");
            args.add(species.name());
        }
        if (status != null) {
            where.append(" AND p.status = ?");
            args.add(status.name());
        }
        Long total = t.jdbc().queryForObject(t.q("SELECT count(*) FROM {s}.pets p WHERE p.deleted_at IS NULL" + where),
                Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add(page * size);
        List<Pet> rows = t.jdbc().query(t.q(SELECT + where + " ORDER BY u.identifier, p.name, p.id LIMIT ? OFFSET ?"),
                MAPPER, pageArgs.toArray());
        return PageResult.of(rows, page, size, total == null ? 0 : total);
    }

    @Override
    public List<Pet> findByUnitIds(List<UUID> unitIds) {
        if (unitIds == null || unitIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(", ", Collections.nCopies(unitIds.size(), "?"));
        return t.jdbc().query(t.q(SELECT + " AND p.unit_id IN (" + placeholders + ") ORDER BY u.identifier, p.name"),
                MAPPER, unitIds.toArray());
    }

    @Override
    public void insert(Pet p) {
        t.jdbc().update(t.q("""
                INSERT INTO {s}.pets (id, unit_id, name, species, breed, color, birth_date, weight_kg, microchip,
                                     vaccinated, vaccine_expires_on, notes, status)
                VALUES (?::uuid, ?::uuid, ?::varchar, ?::varchar, ?::varchar, ?::varchar, ?::date, ?::numeric,
                        ?::varchar, ?::boolean, ?::date, ?::varchar, ?::varchar)
                """), p.id(), p.unitId(), p.name(), p.species().name(), p.breed(), p.color(), p.birthDate(),
                p.weightKg(), p.microchip(), p.vaccinated(), p.vaccineExpiresOn(), p.notes(), p.status().name());
    }

    @Override
    public boolean update(Pet p) {
        return t.jdbc().update(t.q("""
                UPDATE {s}.pets SET unit_id = ?::uuid, name = ?::varchar, species = ?::varchar, breed = ?::varchar,
                       color = ?::varchar, birth_date = ?::date, weight_kg = ?::numeric, microchip = ?::varchar,
                       vaccinated = ?::boolean, vaccine_expires_on = ?::date, notes = ?::varchar, status = ?::varchar,
                       updated_at = now()
                WHERE id = ?::uuid AND deleted_at IS NULL
                """), p.unitId(), p.name(), p.species().name(), p.breed(), p.color(), p.birthDate(), p.weightKg(),
                p.microchip(), p.vaccinated(), p.vaccineExpiresOn(), p.notes(), p.status().name(), p.id()) > 0;
    }

    @Override
    public boolean softDelete(UUID id, UUID by, Instant at) {
        return t.jdbc().update(t.q("UPDATE {s}.pets SET deleted_at = ?, deleted_by = ?, updated_at = now() "
                + "WHERE id = ? AND deleted_at IS NULL"), Jdbc.ts(at), by, id) > 0;
    }

    private static String like(String raw) {
        return "%" + raw.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
                + "%";
    }
}
