package com.codevam.vecindad.unit;

import com.codevam.vecindad.config.AppProperties;
import com.codevam.vecindad.identity.adapter.out.security.JwtService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    private static final String SECRET = "unit-test-secret-unit-test-secret-1234567890";

    private static AppProperties props(String secret, String env) {
        return new AppProperties("Vecindad", "CodeVam", env, new AppProperties.Mail(false),
                new AppProperties.Jwt(secret, "vecindad", Duration.ofMinutes(15), Duration.ofDays(7)),
                new AppProperties.Security(5, Duration.ofMinutes(15), Duration.ofMinutes(30), Duration.ofHours(72), 10, 4, List.of()),
                new AppProperties.Seed(false, "x"));
    }

    @Test
    void issuesAndParsesToken() {
        JwtService jwt = new JwtService(props(SECRET, "test"), Clock.systemUTC());
        UUID user = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        var parsed = jwt.parse(jwt.issue(user, tenant)).orElseThrow();
        assertEquals(user, parsed.userId());
        assertEquals(tenant, parsed.tenantId());
    }

    @Test
    void tokenWithoutTenantParsesWithNullTenant() {
        JwtService jwt = new JwtService(props(SECRET, "test"), Clock.systemUTC());
        assertNull(jwt.parse(jwt.issue(UUID.randomUUID(), null)).orElseThrow().tenantId());
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        JwtService a = new JwtService(props(SECRET, "test"), Clock.systemUTC());
        JwtService b = new JwtService(props("another-secret-another-secret-another-123456", "test"), Clock.systemUTC());
        assertTrue(a.parse(b.issue(UUID.randomUUID(), UUID.randomUUID())).isEmpty());
    }

    @Test
    void rejectsTamperedAndGarbageTokens() {
        JwtService jwt = new JwtService(props(SECRET, "test"), Clock.systemUTC());
        String token = jwt.issue(UUID.randomUUID(), UUID.randomUUID());
        assertTrue(jwt.parse(token + "x").isEmpty());
        assertTrue(jwt.parse("no.es.un.jwt").isEmpty());
        assertTrue(jwt.parse("").isEmpty());
    }

    @Test
    void rejectsExpiredToken() {
        Clock past = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC);
        JwtService jwt = new JwtService(props(SECRET, "test"), past);
        assertTrue(jwt.parse(jwt.issue(UUID.randomUUID(), null)).isEmpty());
    }

    @Test
    void refusesShortSecretAndDevSecretInProduction() {
        assertThrows(IllegalStateException.class, () -> new JwtService(props("short", "test"), Clock.systemUTC()));
        assertThrows(IllegalStateException.class,
                () -> new JwtService(props("dev-only-secret-dev-only-secret-dev-only-secret", "production"), Clock.systemUTC()));
    }
}
