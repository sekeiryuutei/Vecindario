package com.codevam.vecindad.assemblies.adapter.out;

import com.codevam.vecindad.assemblies.application.port.out.AssemblyPort;
import com.codevam.vecindad.assemblies.domain.*;
import com.codevam.vecindad.shared.persistence.Jdbc;
import com.codevam.vecindad.shared.tenancy.TenantJdbc;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcAssemblyAdapter implements AssemblyPort {

    private static final String ASSEMBLY_COLS = "id, title, type, scheduled_at, status, location, is_virtual, meeting_link, required_quorum_percentage, created_at, updated_at";
    private static final String TOPIC_COLS = "id, assembly_id, order_num, title, description, voting_type, status, requires_qualified_majority, options, created_at";
    private static final String ATTENDANCE_COLS = "assembly_id, unit_id, person_id, is_proxy, proxy_name, checked_in_at";
    private static final String VOTE_COLS = "id, topic_id, unit_id, person_id, choice, weight, voted_at";

    private final TenantJdbc t;
    private final ObjectMapper objectMapper;

    public JdbcAssemblyAdapter(TenantJdbc t, ObjectMapper objectMapper) {
        this.t = t;
        this.objectMapper = objectMapper;
    }

    private final RowMapper<Assembly> assemblyMapper = (rs, rowNum) -> new Assembly(
            Jdbc.uuid(rs, "id"),
            rs.getString("title"),
            AssemblyType.valueOf(rs.getString("type")),
            Jdbc.instant(rs, "scheduled_at"),
            AssemblyStatus.valueOf(rs.getString("status")),
            rs.getString("location"),
            rs.getBoolean("is_virtual"),
            rs.getString("meeting_link"),
            rs.getBigDecimal("required_quorum_percentage"),
            Jdbc.instant(rs, "created_at"),
            Jdbc.instant(rs, "updated_at")
    );

    private final RowMapper<AssemblyAttendance> attendanceMapper = (rs, rowNum) -> new AssemblyAttendance(
            Jdbc.uuid(rs, "assembly_id"),
            Jdbc.uuid(rs, "unit_id"),
            Jdbc.uuid(rs, "person_id"),
            rs.getBoolean("is_proxy"),
            rs.getString("proxy_name"),
            Jdbc.instant(rs, "checked_in_at")
    );

    private final RowMapper<AssemblyVote> voteMapper = (rs, rowNum) -> new AssemblyVote(
            Jdbc.uuid(rs, "id"),
            Jdbc.uuid(rs, "topic_id"),
            Jdbc.uuid(rs, "unit_id"),
            Jdbc.uuid(rs, "person_id"),
            rs.getString("choice"),
            rs.getBigDecimal("weight"),
            Jdbc.instant(rs, "voted_at")
    );

    private AssemblyTopic mapTopic(java.sql.ResultSet rs) throws java.sql.SQLException {
        List<String> opts = List.of("A FAVOR", "EN CONTRA", "ABSTENCION");
        String raw = rs.getString("options");
        if (raw != null) {
            try {
                opts = objectMapper.readValue(raw, new TypeReference<List<String>>() {});
            } catch (Exception ignored) {}
        }
        return new AssemblyTopic(
                Jdbc.uuid(rs, "id"),
                Jdbc.uuid(rs, "assembly_id"),
                rs.getInt("order_num"),
                rs.getString("title"),
                rs.getString("description"),
                VotingType.valueOf(rs.getString("voting_type")),
                TopicStatus.valueOf(rs.getString("status")),
                rs.getBoolean("requires_qualified_majority"),
                opts,
                Jdbc.instant(rs, "created_at")
        );
    }

    @Override
    public Assembly insertAssembly(Assembly a) {
        UUID id = a.id() == null ? UUID.randomUUID() : a.id();
        String sql = """
                INSERT INTO {s}.assemblies (
                    id, title, type, scheduled_at, status, location, is_virtual, meeting_link, required_quorum_percentage
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        t.jdbc().update(t.q(sql),
                id, a.title(), a.type().name(), Jdbc.ts(a.scheduledAt()), a.status().name(),
                a.location(), a.isVirtual(), a.meetingLink(), a.requiredQuorumPercentage());
        return findAssemblyById(id).orElseThrow();
    }

    @Override
    public Optional<Assembly> findAssemblyById(UUID id) {
        return t.jdbc().query(t.q("SELECT " + ASSEMBLY_COLS + " FROM {s}.assemblies WHERE id = ?"),
                assemblyMapper, id).stream().findFirst();
    }

    @Override
    public List<Assembly> listAssemblies() {
        return t.jdbc().query(t.q("SELECT " + ASSEMBLY_COLS + " FROM {s}.assemblies ORDER BY scheduled_at DESC"),
                assemblyMapper);
    }

    @Override
    public boolean updateAssemblyStatus(UUID id, AssemblyStatus status) {
        return t.jdbc().update(t.q("UPDATE {s}.assemblies SET status = ?, updated_at = now() WHERE id = ?"),
                status.name(), id) > 0;
    }

    @Override
    public boolean registerAttendance(AssemblyAttendance att) {
        String sql = """
                INSERT INTO {s}.assembly_attendances (assembly_id, unit_id, person_id, is_proxy, proxy_name)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (assembly_id, unit_id) DO UPDATE SET
                    person_id = EXCLUDED.person_id,
                    is_proxy = EXCLUDED.is_proxy,
                    proxy_name = EXCLUDED.proxy_name,
                    checked_in_at = now()
                """;
        return t.jdbc().update(t.q(sql),
                att.assemblyId(), att.unitId(), att.personId(), att.isProxy(), att.proxyName()) > 0;
    }

    @Override
    public List<AssemblyAttendance> listAttendances(UUID assemblyId) {
        return t.jdbc().query(t.q("SELECT " + ATTENDANCE_COLS + " FROM {s}.assembly_attendances WHERE assembly_id = ? ORDER BY checked_in_at ASC"),
                attendanceMapper, assemblyId);
    }

    @Override
    public boolean isUnitCheckedIn(UUID assemblyId, UUID unitId) {
        Integer c = t.jdbc().queryForObject(
                t.q("SELECT count(*) FROM {s}.assembly_attendances WHERE assembly_id = ? AND unit_id = ?"),
                Integer.class, assemblyId, unitId);
        return c != null && c > 0;
    }

    @Override
    public QuorumSummary calculateQuorum(UUID assemblyId) {
        Assembly a = findAssemblyById(assemblyId).orElseThrow();

        Long totalUnits = t.jdbc().queryForObject(
                t.q("SELECT count(*) FROM {s}.property_units WHERE status = 'ACTIVE' AND deleted_at IS NULL"), Long.class);
        BigDecimal totalCoeff = t.jdbc().queryForObject(
                t.q("SELECT COALESCE(SUM(coefficient), 0) FROM {s}.property_units WHERE status = 'ACTIVE' AND deleted_at IS NULL"), BigDecimal.class);

        Long regUnits = t.jdbc().queryForObject(
                t.q("SELECT count(*) FROM {s}.assembly_attendances WHERE assembly_id = ?"), Long.class, assemblyId);
        BigDecimal regCoeff = t.jdbc().queryForObject(
                t.q("""
                    SELECT COALESCE(SUM(u.coefficient), 0)
                    FROM {s}.assembly_attendances att
                    JOIN {s}.property_units u ON u.id = att.unit_id
                    WHERE att.assembly_id = ?
                    """), BigDecimal.class, assemblyId);

        if (totalCoeff == null || totalCoeff.compareTo(BigDecimal.ZERO) == 0) {
            totalCoeff = BigDecimal.ONE;
        }
        if (regCoeff == null) {
            regCoeff = BigDecimal.ZERO;
        }

        BigDecimal currentPct = regCoeff.divide(totalCoeff, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
        boolean hasQuorum = currentPct.compareTo(a.requiredQuorumPercentage()) >= 0;

        return new QuorumSummary(
                regUnits != null ? regUnits : 0,
                totalUnits != null ? totalUnits : 0,
                currentPct,
                a.requiredQuorumPercentage(),
                hasQuorum
        );
    }

    @Override
    public AssemblyTopic insertTopic(AssemblyTopic top) {
        UUID id = top.id() == null ? UUID.randomUUID() : top.id();
        String jsonOpts = "[\"A FAVOR\", \"EN CONTRA\", \"ABSTENCION\"]";
        try {
            jsonOpts = objectMapper.writeValueAsString(top.options());
        } catch (Exception ignored) {}

        String sql = """
                INSERT INTO {s}.assembly_topics (
                    id, assembly_id, order_num, title, description, voting_type, status,
                    requires_qualified_majority, options
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """;
        t.jdbc().update(t.q(sql),
                id, top.assemblyId(), top.orderNum(), top.title(), top.description(),
                top.votingType().name(), top.status().name(), top.requiresQualifiedMajority(),
                jsonOpts);
        return findTopicById(id).orElseThrow();
    }

    @Override
    public Optional<AssemblyTopic> findTopicById(UUID topicId) {
        return t.jdbc().query(t.q("SELECT " + TOPIC_COLS + " FROM {s}.assembly_topics WHERE id = ?"),
                (rs, rowNum) -> mapTopic(rs), topicId).stream().findFirst();
    }

    @Override
    public List<AssemblyTopic> listTopics(UUID assemblyId) {
        return t.jdbc().query(t.q("SELECT " + TOPIC_COLS + " FROM {s}.assembly_topics WHERE assembly_id = ? ORDER BY order_num ASC"),
                (rs, rowNum) -> mapTopic(rs), assemblyId);
    }

    @Override
    public boolean updateTopicStatus(UUID topicId, TopicStatus status) {
        return t.jdbc().update(t.q("UPDATE {s}.assembly_topics SET status = ? WHERE id = ?"),
                status.name(), topicId) > 0;
    }

    @Override
    public boolean insertVote(AssemblyVote v) {
        UUID id = v.id() == null ? UUID.randomUUID() : v.id();
        String sql = """
                INSERT INTO {s}.assembly_votes (id, topic_id, unit_id, person_id, choice, weight)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        return t.jdbc().update(t.q(sql),
                id, v.topicId(), v.unitId(), v.personId(), v.choice(), v.weight()) > 0;
    }

    @Override
    public List<AssemblyVote> listVotes(UUID topicId) {
        return t.jdbc().query(t.q("SELECT " + VOTE_COLS + " FROM {s}.assembly_votes WHERE topic_id = ?"),
                voteMapper, topicId);
    }

    @Override
    public boolean hasUnitVoted(UUID topicId, UUID unitId) {
        Integer c = t.jdbc().queryForObject(
                t.q("SELECT count(*) FROM {s}.assembly_votes WHERE topic_id = ? AND unit_id = ?"),
                Integer.class, topicId, unitId);
        return c != null && c > 0;
    }

    @Override
    public BigDecimal getUnitCoefficient(UUID unitId) {
        BigDecimal coeff = t.jdbc().queryForObject(
                t.q("SELECT coefficient FROM {s}.property_units WHERE id = ?"), BigDecimal.class, unitId);
        return coeff != null ? coeff : BigDecimal.ONE;
    }
}

