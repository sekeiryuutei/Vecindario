package com.codevam.vecindad.identity.adapter.out.persistence;

import com.codevam.vecindad.identity.application.port.out.UserPort;
import com.codevam.vecindad.identity.domain.User;
import com.codevam.vecindad.identity.domain.UserStatus;
import com.codevam.vecindad.shared.persistence.Jdbc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcUserAdapter implements UserPort {
    private static final String COLS = "id, email, full_name, password_hash, platform_role, status, failed_attempts, "
            + "locked_until, last_tenant_id";

    private static final RowMapper<User> MAPPER = (rs, i) -> new User(
            Jdbc.uuid(rs, "id"), rs.getString("email"), rs.getString("full_name"), rs.getString("password_hash"),
            rs.getString("platform_role"), UserStatus.valueOf(rs.getString("status")), rs.getInt("failed_attempts"),
            Jdbc.instant(rs, "locked_until"), Jdbc.uuid(rs, "last_tenant_id"));

    private final JdbcTemplate jdbc;

    public JdbcUserAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<User> findByEmail(String emailLowerCase) {
        return jdbc.query("SELECT " + COLS + " FROM public.users WHERE lower(email) = ?", MAPPER, emailLowerCase)
                .stream().findFirst();
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jdbc.query("SELECT " + COLS + " FROM public.users WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    @Override
    public User insert(String email, String fullName, String passwordHash, UserStatus status, String platformRole) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO public.users (id, email, full_name, password_hash, status, platform_role) "
                + "VALUES (?, ?, ?, ?, ?, ?)", id, email.toLowerCase(), fullName, passwordHash, status.name(), platformRole);
        return findById(id).orElseThrow();
    }

    @Override
    public void recordLoginFailure(UUID id, int attempts, Instant lockedUntil) {
        jdbc.update("UPDATE public.users SET failed_attempts = ?, locked_until = ?, updated_at = now() WHERE id = ?",
                attempts, Jdbc.ts(lockedUntil), id);
    }

    @Override
    public void recordLoginSuccess(UUID id, Instant at) {
        jdbc.update("UPDATE public.users SET failed_attempts = 0, locked_until = NULL, last_login_at = ?, "
                + "updated_at = now() WHERE id = ?", Jdbc.ts(at), id);
    }

    @Override
    public void updatePassword(UUID id, String passwordHash) {
        jdbc.update("UPDATE public.users SET password_hash = ?, failed_attempts = 0, locked_until = NULL, "
                + "updated_at = now() WHERE id = ?", passwordHash, id);
    }

    @Override
    public void activateWithPassword(UUID id, String passwordHash) {
        jdbc.update("UPDATE public.users SET password_hash = ?, status = 'ACTIVE', failed_attempts = 0, "
                + "locked_until = NULL, updated_at = now() WHERE id = ? AND status <> 'DISABLED'", passwordHash, id);
    }

    @Override
    public void setLastTenant(UUID id, UUID tenantId) {
        jdbc.update("UPDATE public.users SET last_tenant_id = ? WHERE id = ?", tenantId, id);
    }
}
