package com.codevam.vecindad.communications.adapter.out;

import com.codevam.vecindad.communications.application.port.out.CommunicationPort;
import com.codevam.vecindad.communications.domain.Communication;
import com.codevam.vecindad.communications.domain.CommunicationPriority;
import com.codevam.vecindad.communications.domain.CommunicationStatus;
import com.codevam.vecindad.communications.domain.TargetAudience;
import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.persistence.Jdbc;
import com.codevam.vecindad.shared.tenancy.TenantJdbc;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcCommunicationAdapter implements CommunicationPort {

    private static final String COLS = """
            id, title, summary, content, priority, target_audience, target_tower,
            status, is_pinned, created_by, published_at, expires_at, created_at, updated_at
            """;

    private static final RowMapper<Communication> MAPPER = (rs, rowNum) -> new Communication(
            Jdbc.uuid(rs, "id"),
            rs.getString("title"),
            rs.getString("summary"),
            rs.getString("content"),
            CommunicationPriority.valueOf(rs.getString("priority")),
            TargetAudience.valueOf(rs.getString("target_audience")),
            rs.getString("target_tower"),
            CommunicationStatus.valueOf(rs.getString("status")),
            rs.getBoolean("is_pinned"),
            Jdbc.uuid(rs, "created_by"),
            Jdbc.instant(rs, "published_at"),
            Jdbc.instant(rs, "expires_at"),
            Jdbc.instant(rs, "created_at"),
            Jdbc.instant(rs, "updated_at")
    );

    private final TenantJdbc t;

    public JdbcCommunicationAdapter(TenantJdbc t) {
        this.t = t;
    }

    @Override
    public Communication insert(Communication c) {
        UUID id = c.id() == null ? UUID.randomUUID() : c.id();
        String sql = """
                INSERT INTO {s}.communications (
                    id, title, summary, content, priority, target_audience, target_tower,
                    status, is_pinned, created_by, published_at, expires_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        t.jdbc().update(t.q(sql),
                id, c.title(), c.summary(), c.content(), c.priority().name(),
                c.targetAudience().name(), c.targetTower(), c.status().name(),
                c.isPinned(), c.createdBy(), Jdbc.ts(c.publishedAt()), Jdbc.ts(c.expiresAt()));
        return findById(id).orElseThrow();
    }

    @Override
    public Optional<Communication> findById(UUID id) {
        return t.jdbc().query(t.q("SELECT " + COLS + " FROM {s}.communications WHERE id = ?"), MAPPER, id)
                .stream().findFirst();
    }

    @Override
    public PageResult<Communication> search(CommunicationStatus status, int page, int size) {
        String where = status != null ? " WHERE status = ?" : "";
        Object[] countArgs = status != null ? new Object[]{status.name()} : new Object[]{};
        Long total = t.jdbc().queryForObject(t.q("SELECT count(*) FROM {s}.communications" + where), Long.class, countArgs);

        List<Object> args = new ArrayList<>();
        if (status != null) {
            args.add(status.name());
        }
        args.add(size);
        args.add(page * size);

        List<Communication> rows = t.jdbc().query(t.q("SELECT " + COLS + " FROM {s}.communications" + where
                + " ORDER BY is_pinned DESC, published_at DESC LIMIT ? OFFSET ?"), MAPPER, args.toArray());
        return PageResult.of(rows, page, size, total == null ? 0 : total);
    }

    @Override
    public PageResult<Communication> searchForAudience(TargetAudience audience, String tower, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE status = 'PUBLISHED' AND (expires_at IS NULL OR expires_at > now())");
        List<Object> args = new ArrayList<>();
        if (audience != null && audience != TargetAudience.ALL) {
            where.append(" AND (target_audience = 'ALL' OR target_audience = ?)");
            args.add(audience.name());
        }
        if (tower != null && !tower.isBlank()) {
            where.append(" AND (target_tower IS NULL OR target_tower = ?)");
            args.add(tower);
        }
        Long total = t.jdbc().queryForObject(t.q("SELECT count(*) FROM {s}.communications" + where), Long.class, args.toArray());

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add(page * size);

        List<Communication> rows = t.jdbc().query(t.q("SELECT " + COLS + " FROM {s}.communications" + where
                + " ORDER BY is_pinned DESC, published_at DESC LIMIT ? OFFSET ?"), MAPPER, pageArgs.toArray());
        return PageResult.of(rows, page, size, total == null ? 0 : total);
    }

    @Override
    public boolean update(Communication c) {
        String sql = """
                UPDATE {s}.communications SET
                    title = ?, summary = ?, content = ?, priority = ?, target_audience = ?,
                    target_tower = ?, status = ?, is_pinned = ?, expires_at = ?, updated_at = now()
                WHERE id = ?
                """;
        return t.jdbc().update(t.q(sql),
                c.title(), c.summary(), c.content(), c.priority().name(),
                c.targetAudience().name(), c.targetTower(), c.status().name(),
                c.isPinned(), Jdbc.ts(c.expiresAt()), c.id()) > 0;
    }

    @Override
    public boolean delete(UUID id) {
        return t.jdbc().update(t.q("DELETE FROM {s}.communications WHERE id = ?"), id) > 0;
    }

    @Override
    public void markRead(UUID communicationId, UUID userId) {
        t.jdbc().update(t.q("INSERT INTO {s}.communication_reads (communication_id, user_id) VALUES (?, ?) ON CONFLICT DO NOTHING"),
                communicationId, userId);
    }

    @Override
    public boolean isRead(UUID communicationId, UUID userId) {
        Integer c = t.jdbc().queryForObject(
                t.q("SELECT count(*) FROM {s}.communication_reads WHERE communication_id = ? AND user_id = ?"),
                Integer.class, communicationId, userId);
        return c != null && c > 0;
    }
}

