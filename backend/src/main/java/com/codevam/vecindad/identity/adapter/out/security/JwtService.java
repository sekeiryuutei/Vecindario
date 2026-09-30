package com.codevam.vecindad.identity.adapter.out.security;

import com.codevam.vecindad.config.AppProperties;
import com.codevam.vecindad.identity.application.port.out.AccessTokenPort;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * El access token solo contiene: sub (userId), tid (tenantId opcional), jti, iat, exp e iss.
 * Rol, permisos y estado se consultan en BD en cada petición para que las revocaciones sean inmediatas.
 */
@Component
public class JwtService implements AccessTokenPort {

    public record Parsed(UUID userId, UUID tenantId) {}

    private final SecretKey key;
    private final String issuer;
    private final Duration ttl;
    private final Clock clock;

    public JwtService(AppProperties props, Clock clock) {
        String secret = props.jwt().secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET es obligatorio y debe tener al menos 32 caracteres.");
        }
        if (props.isProduction() && secret.startsWith("dev-only")) {
            throw new IllegalStateException("No se permite el JWT_SECRET de desarrollo en producción.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = props.jwt().issuer();
        this.ttl = props.jwt().accessExpiration();
        this.clock = clock;
    }

    @Override
    public String issue(UUID userId, UUID tenantId) {
        Instant now = clock.instant();
        var builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(issuer)
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)));
        if (tenantId != null) {
            builder.claim("tid", tenantId.toString());
        }
        return builder.signWith(key, Jwts.SIG.HS256).compact();
    }

    @Override
    public long expiresInSeconds() {
        return ttl.toSeconds();
    }

    public Optional<Parsed> parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).requireIssuer(issuer).build()
                    .parseSignedClaims(token).getPayload();
            UUID userId = UUID.fromString(claims.getSubject());
            String tid = claims.get("tid", String.class);
            return Optional.of(new Parsed(userId, tid == null ? null : UUID.fromString(tid)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
