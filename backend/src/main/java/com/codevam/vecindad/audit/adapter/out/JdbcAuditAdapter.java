package com.codevam.vecindad.audit.adapter.out;

import com.codevam.vecindad.audit.application.port.out.AuditPort;
import com.codevam.vecindad.audit.domain.AuditLogView;
import com.codevam.vecindad.audit.domain.AuditRecord;
import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.persistence.Jdbc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcAuditAdapter implements AuditPort {
    private static final RowMapper<AuditLogView> MAPPER = (rs, i) -> new AuditLogView(
            Jdbc.uuid(rs, "id"), Jdbc.instant(rs, "occurred_at"), Jdbc.uuid(rs, "tenant_id"),
            Jdbc.uuid(rs, "actor_user_id"), rs.getString("actor_email"), rs.getString("action"),
            rs.getString("entity"), rs.getString("entity_id"), rs.getString("result"),
            rs.getString("ip"), rs.getString("trace_id"), rs.getString("details"));

    private final JdbcTemplate jdbc;

    public JdbcAuditAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(AuditRecord r) {
        jdbc.update("""
                INSERT INTO public.audit_logs
                  (id, occurred_at, tenant_id, actor_user_id, actor_email, action, entity, entity_id, result,
                   ip, user_agent, trace_id, details)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """,
                r.id(), Jdbc.ts(r.occurredAt()), r.tenantId(), r.actorUserId(), r.actorEmail(), r.action(),
                r.entity(), r.entityId(), r.success() ? "SUCCESS" : "FAILURE", r.ip(), r.userAgent(),
                r.traceId(), r.detailsJson());
    }

    @Override
    public PageResult<AuditLogView> search(UUID tenantId, String action, Instant from, Instant to, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (tenantId != null) { where.append(" AND tenant_id = ?"); args.add(tenantId); }
        if (action != null) { where.append(" AND action = ?"); args.add(action); }
        if (from != null) { where.append(" AND occurred_at >= ?"); args.add(Jdbc.ts(from)); }
        if (to != null) { where.append(" AND occurred_at < ?"); args.add(Jdbc.ts(to)); }

        Long total = jdbc.queryForObject("SELECT count(*) FROM public.audit_logs" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add(page * size);
        List<AuditLogView> rows = jdbc.query(
                "SELECT id, occurred_at, tenant_id, actor_user_id, actor_email, action, entity, entity_id, result, ip, trace_id, "
                        + "details::text AS details FROM public.audit_logs" + where
                        + " ORDER BY occurred_at DESC, id LIMIT ? OFFSET ?",
                MAPPER, pageArgs.toArray());
        return PageResult.of(rows, page, size, total == null ? 0 : total);
    }
}
