package com.codevam.vecindad.people.adapter.out;

import com.codevam.vecindad.people.application.port.out.PersonPort;
import com.codevam.vecindad.people.application.port.out.UnitRelationPort;
import com.codevam.vecindad.people.domain.MyUnit;
import com.codevam.vecindad.people.domain.Person;
import com.codevam.vecindad.people.domain.RelationType;
import com.codevam.vecindad.people.domain.UnitRelation;
import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.persistence.Jdbc;
import com.codevam.vecindad.shared.tenancy.TenantJdbc;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcPersonAdapter implements PersonPort, UnitRelationPort {
    private static final String PERSON_COLS = "id, document_type, document_number, full_name, email, phone, user_id, created_at, updated_at";

    private static final RowMapper<Person> PERSON = (rs, i) -> new Person(Jdbc.uuid(rs, "id"), rs.getString("document_type"),
            rs.getString("document_number"), rs.getString("full_name"), rs.getString("email"), rs.getString("phone"),
            Jdbc.uuid(rs, "user_id"), Jdbc.instant(rs, "created_at"), Jdbc.instant(rs, "updated_at"));

    private static final String REL_SELECT = """
            SELECT r.id, r.unit_id, u.identifier AS unit_identifier, r.person_id, p.full_name AS person_name,
                   r.relation_type, r.start_date, r.end_date, r.status
            FROM {s}.unit_relations r
            JOIN {s}.property_units u ON u.id = r.unit_id
            JOIN {s}.people p ON p.id = r.person_id
            """;

    private static final RowMapper<UnitRelation> RELATION = (rs, i) -> new UnitRelation(Jdbc.uuid(rs, "id"),
            Jdbc.uuid(rs, "unit_id"), rs.getString("unit_identifier"), Jdbc.uuid(rs, "person_id"),
            rs.getString("person_name"), RelationType.valueOf(rs.getString("relation_type")),
            localDate(rs.getDate("start_date")), localDate(rs.getDate("end_date")), rs.getString("status"));

    private final TenantJdbc t;

    public JdbcPersonAdapter(TenantJdbc t) {
        this.t = t;
    }

    private static LocalDate localDate(Date d) {
        return d == null ? null : d.toLocalDate();
    }

    // ---------------------------------------------------------------- PersonPort

    @Override
    public Person insert(Person p) {
        t.jdbc().update(t.q("INSERT INTO {s}.people (id, document_type, document_number, full_name, email, phone) "
                + "VALUES (?, ?, ?, ?, ?, ?)"), p.id(), p.documentType(), p.documentNumber(), p.fullName(), p.email(), p.phone());
        return findById(p.id()).orElseThrow();
    }

    @Override
    public Optional<Person> findById(UUID id) {
        return t.jdbc().query(t.q("SELECT " + PERSON_COLS + " FROM {s}.people WHERE id = ? AND deleted_at IS NULL"), PERSON, id)
                .stream().findFirst();
    }

    @Override
    public boolean update(Person p) {
        return t.jdbc().update(t.q("UPDATE {s}.people SET document_type = ?, document_number = ?, full_name = ?, email = ?, "
                + "phone = ?, updated_at = now() WHERE id = ? AND deleted_at IS NULL"),
                p.documentType(), p.documentNumber(), p.fullName(), p.email(), p.phone(), p.id()) > 0;
    }

    @Override
    public boolean softDelete(UUID id, UUID by, Instant at) {
        return t.jdbc().update(t.q("UPDATE {s}.people SET deleted_at = ?, deleted_by = ?, user_id = NULL "
                + "WHERE id = ? AND deleted_at IS NULL"), Jdbc.ts(at), by, id) > 0;
    }

    @Override
    public PageResult<Person> search(String q, int page, int size) {
        String where = " WHERE deleted_at IS NULL";
        List<Object> args = new ArrayList<>();
        if (q != null) {
            String like = "%" + q.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            where += " AND (lower(full_name) LIKE ? OR lower(coalesce(email,'')) LIKE ? OR coalesce(document_number,'') LIKE ?)";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        Long total = t.jdbc().queryForObject(t.q("SELECT count(*) FROM {s}.people" + where), Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add(page * size);
        List<Person> rows = t.jdbc().query(t.q("SELECT " + PERSON_COLS + " FROM {s}.people" + where
                + " ORDER BY full_name, id LIMIT ? OFFSET ?"), PERSON, pageArgs.toArray());
        return PageResult.of(rows, page, size, total == null ? 0 : total);
    }

    @Override
    public boolean documentExists(String type, String number, UUID excludeId) {
        Integer n = t.jdbc().queryForObject(t.q("SELECT count(*) FROM {s}.people WHERE document_type = ? AND document_number = ? "
                + "AND deleted_at IS NULL AND (?::uuid IS NULL OR id <> ?::uuid)"), Integer.class, type, number, excludeId, excludeId);
        return n != null && n > 0;
    }

    @Override
    public void linkUser(UUID personId, UUID userId) {
        t.jdbc().update(t.q("UPDATE {s}.people SET user_id = ?, updated_at = now() WHERE id = ? AND deleted_at IS NULL"), userId, personId);
    }

    @Override
    public Optional<Person> findByUserId(UUID userId) {
        return t.jdbc().query(t.q("SELECT " + PERSON_COLS + " FROM {s}.people WHERE user_id = ? AND deleted_at IS NULL"), PERSON, userId)
                .stream().findFirst();
    }

    @Override
    public boolean hasActiveRelations(UUID personId) {
        Integer n = t.jdbc().queryForObject(t.q("SELECT count(*) FROM {s}.unit_relations WHERE person_id = ? AND status = 'ACTIVE'"),
                Integer.class, personId);
        return n != null && n > 0;
    }

    // ------------------------------------------------------------ UnitRelationPort

    @Override
    public UnitRelation add(UUID unitId, UUID personId, RelationType type, LocalDate start, UUID by) {
        UUID id = UUID.randomUUID();
        t.jdbc().update(t.q("INSERT INTO {s}.unit_relations (id, unit_id, person_id, relation_type, start_date, created_by) "
                + "VALUES (?, ?, ?, ?, ?, ?)"), id, unitId, personId, type.name(), start == null ? null : Date.valueOf(start), by);
        return find(id).orElseThrow();
    }

    @Override
    public boolean end(UUID relationId, LocalDate end, UUID by) {
        return t.jdbc().update(t.q("UPDATE {s}.unit_relations SET status = 'ENDED', end_date = ?, ended_at = now(), ended_by = ? "
                + "WHERE id = ? AND status = 'ACTIVE'"), Date.valueOf(end), by, relationId) > 0;
    }

    @Override
    public Optional<UnitRelation> find(UUID id) {
        return t.jdbc().query(t.q(REL_SELECT + " WHERE r.id = ?"), RELATION, id).stream().findFirst();
    }

    @Override
    public List<UnitRelation> listByUnit(UUID unitId) {
        return t.jdbc().query(t.q(REL_SELECT + " WHERE r.unit_id = ? ORDER BY r.status, p.full_name"), RELATION, unitId);
    }

    @Override
    public List<UnitRelation> listByPerson(UUID personId) {
        return t.jdbc().query(t.q(REL_SELECT + " WHERE r.person_id = ? ORDER BY r.status, u.identifier"), RELATION, personId);
    }

    @Override
    public List<MyUnit> unitsOfUser(UUID userId) {
        return t.jdbc().query(t.q("""
                SELECT u.id, u.identifier, u.tower, u.unit_type, array_agg(DISTINCT r.relation_type) AS relations
                FROM {s}.unit_relations r
                JOIN {s}.people p ON p.id = r.person_id
                JOIN {s}.property_units u ON u.id = r.unit_id
                WHERE p.user_id = ? AND p.deleted_at IS NULL AND r.status = 'ACTIVE' AND u.deleted_at IS NULL
                GROUP BY u.id, u.identifier, u.tower, u.unit_type
                ORDER BY u.identifier
                """), (rs, i) -> new MyUnit(Jdbc.uuid(rs, "id"), rs.getString("identifier"), rs.getString("tower"),
                rs.getString("unit_type"), relationsOf(rs)), userId);
    }

    private static List<String> relationsOf(ResultSet rs) throws SQLException {
        Object[] arr = (Object[]) rs.getArray("relations").getArray();
        return Arrays.stream(arr).map(Object::toString).toList();
    }

    @Override
    public boolean userHasUnit(UUID userId, UUID unitId) {
        Integer n = t.jdbc().queryForObject(t.q("""
                SELECT count(*) FROM {s}.unit_relations r JOIN {s}.people p ON p.id = r.person_id
                WHERE p.user_id = ? AND r.unit_id = ? AND r.status = 'ACTIVE' AND p.deleted_at IS NULL
                """), Integer.class, userId, unitId);
        return n != null && n > 0;
    }
}
