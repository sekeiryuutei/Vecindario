package com.codevam.vecindad.identity.adapter.out.persistence;

import com.codevam.vecindad.identity.application.port.out.RefreshTokenPort;
import com.codevam.vecindad.identity.domain.RefreshToken;
import com.codevam.vecindad.shared.persistence.Jdbc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcRefreshTokenAdapter implements RefreshTokenPort {
    private final JdbcTemplate jdbc;

    public JdbcRefreshTokenAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UUID save(UUID userId, String tokenHash, Instant expiresAt, String ip, String userAgent) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO public.refresh_tokens (id, user_id, token_hash, expires_at, ip, user_agent) "
                + "VALUES (?, ?, ?, ?, ?, ?)", id, userId, tokenHash, Jdbc.ts(expiresAt), ip, userAgent);
        return id;
    }

    @Override
    public Optional<RefreshToken> findByHash(String tokenHash) {
        return jdbc.query("SELECT id, user_id, expires_at, revoked_at FROM public.refresh_tokens WHERE token_hash = ?",
                (rs, i) -> new RefreshToken(Jdbc.uuid(rs, "id"), Jdbc.uuid(rs, "user_id"),
                        Jdbc.instant(rs, "expires_at"), Jdbc.instant(rs, "revoked_at")), tokenHash).stream().findFirst();
    }

    @Override
    public void revoke(UUID id, UUID replacedBy, Instant at) {
        jdbc.update("UPDATE public.refresh_tokens SET revoked_at = ?, replaced_by = ? WHERE id = ? AND revoked_at IS NULL",
                Jdbc.ts(at), replacedBy, id);
    }

    @Override
    public void revokeAllForUser(UUID userId, Instant at) {
        jdbc.update("UPDATE public.refresh_tokens SET revoked_at = ? WHERE user_id = ? AND revoked_at IS NULL",
                Jdbc.ts(at), userId);
    }
}
