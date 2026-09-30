package com.codevam.vecindad.identity.adapter.out.persistence;

import com.codevam.vecindad.identity.application.port.out.MembershipPort;
import com.codevam.vecindad.identity.domain.*;
import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.persistence.Jdbc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcMembershipAdapter implements MembershipPort {
    private static final String ACCESS_SELECT = """
            SELECT t.id AS tenant_id, t.name, t.slug, t.schema_name, t.company_id, m.role_code
            FROM public.tenant_memberships m
            JOIN public.tenants t ON t.id = m.tenant_id
            JOIN public.users u ON u.id = m.user_id
            WHERE m.user_id = ? AND m.status = 'ACTIVE' AND t.status = 'ACTIVE' AND u.status = 'ACTIVE'
            """;

    private static final RowMapper<TenantAccess> ACCESS = (rs, i) -> new TenantAccess(
            Jdbc.uuid(rs, "tenant_id"), rs.getString("name"), rs.getString("slug"), rs.getString("schema_name"),
            Jdbc.uuid(rs, "company_id"), rs.getString("role_code"));

    private static final RowMapper<Membership> MEMBERSHIP = (rs, i) -> new Membership(
            Jdbc.uuid(rs, "id"), Jdbc.uuid(rs, "user_id"), Jdbc.uuid(rs, "tenant_id"), rs.getString("role_code"),
            MembershipStatus.valueOf(rs.getString("status")), Jdbc.instant(rs, "granted_at"),
            Jdbc.instant(rs, "revoked_at"), Jdbc.instant(rs, "last_activity_at"));

    private static final RowMapper<MemberView> MEMBER_VIEW = (rs, i) -> new MemberView(
            Jdbc.uuid(rs, "user_id"), rs.getString("email"), rs.getString("full_name"),
            UserStatus.valueOf(rs.getString("user_status")), rs.getString("role_code"),
            MembershipStatus.valueOf(rs.getString("status")), Jdbc.instant(rs, "granted_at"),
            Jdbc.instant(rs, "last_activity_at"));

    private static final String MEMBER_VIEW_SELECT = """
            SELECT u.id AS user_id, u.email, u.full_name, u.status AS user_status, m.role_code, m.status,
                   m.granted_at, m.last_activity_at
            FROM public.tenant_memberships m JOIN public.users u ON u.id = m.user_id
            """;

    private final JdbcTemplate jdbc;

    public JdbcMembershipAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<TenantAccess> findActiveAccess(UUID userId, UUID tenantId) {
        return jdbc.query(ACCESS_SELECT + " AND m.tenant_id = ?", ACCESS, userId, tenantId).stream().findFirst();
    }

    @Override
    public List<TenantAccess> listActiveAccesses(UUID userId) {
        return jdbc.query(ACCESS_SELECT + " ORDER BY t.name", ACCESS, userId);
    }

    @Override
    public Optional<Membership> find(UUID userId, UUID tenantId) {
        return jdbc.query("SELECT id, user_id, tenant_id, role_code, status, granted_at, revoked_at, last_activity_at "
                + "FROM public.tenant_memberships WHERE user_id = ? AND tenant_id = ?", MEMBERSHIP, userId, tenantId)
                .stream().findFirst();
    }

    @Override
    public void grant(UUID userId, UUID tenantId, String roleCode, UUID grantedBy) {
        jdbc.update("""
                INSERT INTO public.tenant_memberships (id, user_id, tenant_id, role_code, status, granted_by)
                VALUES (?, ?, ?, ?, 'ACTIVE', ?)
                ON CONFLICT (user_id, tenant_id) DO UPDATE
                  SET role_code = EXCLUDED.role_code, status = 'ACTIVE', granted_at = now(), granted_by = EXCLUDED.granted_by,
                      revoked_at = NULL, revoked_by = NULL, updated_at = now()
                """, UUID.randomUUID(), userId, tenantId, roleCode, grantedBy);
    }

    @Override
    public void updateRole(UUID userId, UUID tenantId, String roleCode) {
        jdbc.update("UPDATE public.tenant_memberships SET role_code = ?, updated_at = now() WHERE user_id = ? AND tenant_id = ?",
                roleCode, userId, tenantId);
    }

    @Override
    public void updateStatus(UUID userId, UUID tenantId, MembershipStatus status, UUID actor) {
        if (status == MembershipStatus.REVOKED) {
            jdbc.update("UPDATE public.tenant_memberships SET status = 'REVOKED', revoked_at = now(), revoked_by = ?, "
                    + "updated_at = now() WHERE user_id = ? AND tenant_id = ?", actor, userId, tenantId);
        } else {
            jdbc.update("UPDATE public.tenant_memberships SET status = ?, updated_at = now() WHERE user_id = ? AND tenant_id = ?",
                    status.name(), userId, tenantId);
        }
    }

    @Override
    public void touchActivity(UUID userId, UUID tenantId) {
        jdbc.update("UPDATE public.tenant_memberships SET last_activity_at = now() WHERE user_id = ? AND tenant_id = ?",
                userId, tenantId);
    }

    @Override
    public PageResult<MemberView> list(UUID tenantId, int page, int size) {
        Long total = jdbc.queryForObject("SELECT count(*) FROM public.tenant_memberships WHERE tenant_id = ?", Long.class, tenantId);
        List<MemberView> rows = jdbc.query(MEMBER_VIEW_SELECT + " WHERE m.tenant_id = ? ORDER BY u.full_name, u.id LIMIT ? OFFSET ?",
                MEMBER_VIEW, tenantId, size, page * size);
        return PageResult.of(rows, page, size, total == null ? 0 : total);
    }

    @Override
    public Optional<MemberView> findView(UUID tenantId, UUID userId) {
        return jdbc.query(MEMBER_VIEW_SELECT + " WHERE m.tenant_id = ? AND m.user_id = ?", MEMBER_VIEW, tenantId, userId)
                .stream().findFirst();
    }

    @Override
    public List<AccessHistoryEntry> history(UUID userId, UUID tenantId) {
        return jdbc.query("SELECT event, role_code, actor_user_id, occurred_at, ip FROM public.tenant_access_history "
                        + "WHERE user_id = ? AND tenant_id = ? ORDER BY occurred_at DESC, id LIMIT 500",
                (rs, i) -> new AccessHistoryEntry(AccessEvent.valueOf(rs.getString("event")), rs.getString("role_code"),
                        Jdbc.uuid(rs, "actor_user_id"), Jdbc.instant(rs, "occurred_at"), rs.getString("ip")),
                userId, tenantId);
    }

    @Override
    public void addHistory(UUID userId, UUID tenantId, AccessEvent event, String roleCode, UUID actor, String ip) {
        jdbc.update("INSERT INTO public.tenant_access_history (id, user_id, tenant_id, event, role_code, actor_user_id, ip) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID(), userId, tenantId, event.name(), roleCode, actor, ip);
    }
}
