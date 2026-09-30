package com.codevam.vecindad.identity.adapter.out.persistence;

import com.codevam.vecindad.identity.application.port.out.OneTimeTokenPort;
import com.codevam.vecindad.identity.domain.OneTimeToken;
import com.codevam.vecindad.shared.persistence.Jdbc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcOneTimeTokenAdapter implements OneTimeTokenPort {
    private final JdbcTemplate jdbc;

    public JdbcOneTimeTokenAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void create(UUID userId, String purpose, String tokenHash, Instant expiresAt) {
        jdbc.update("INSERT INTO public.one_time_tokens (id, user_id, purpose, token_hash, expires_at) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), userId, purpose, tokenHash, Jdbc.ts(expiresAt));
    }

    @Override
    public Optional<OneTimeToken> findUsable(String tokenHash, Instant now) {
        return jdbc.query("SELECT id, user_id, purpose, expires_at FROM public.one_time_tokens "
                        + "WHERE token_hash = ? AND used_at IS NULL AND expires_at > ?",
                (rs, i) -> new OneTimeToken(Jdbc.uuid(rs, "id"), Jdbc.uuid(rs, "user_id"), rs.getString("purpose"),
                        Jdbc.instant(rs, "expires_at")), tokenHash, Jdbc.ts(now)).stream().findFirst();
    }

    @Override
    public void markUsed(UUID id, Instant at) {
        jdbc.update("UPDATE public.one_time_tokens SET used_at = ? WHERE id = ?", Jdbc.ts(at), id);
    }
}
