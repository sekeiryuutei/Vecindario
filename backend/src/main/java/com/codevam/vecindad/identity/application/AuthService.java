package com.codevam.vecindad.identity.application;

import com.codevam.vecindad.audit.application.AuditService;
import com.codevam.vecindad.config.AppProperties;
import com.codevam.vecindad.identity.application.port.out.*;
import com.codevam.vecindad.identity.domain.*;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.security.AuthenticatedUser;
import com.codevam.vecindad.shared.security.CurrentUser;
import com.codevam.vecindad.shared.security.PasswordPolicy;
import com.codevam.vecindad.shared.security.Tokens;
import com.codevam.vecindad.shared.web.ClientInfo;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthService {

    public record UserView(UUID id, String email, String fullName, String platformRole) {}
    public record TenantSummary(UUID id, String name, String slug, String role) {}
    public record SessionResult(String accessToken, String refreshToken, String tokenType, long expiresIn, UserView user,
                                List<TenantSummary> tenants, UUID activeTenantId, String activeRole,
                                Set<String> permissions) {}
    public record TenantTokenResult(String accessToken, String tokenType, long expiresIn, UUID tenantId,
                                    String tenantName, String role, Set<String> permissions) {}
    public record MeResult(UserView user, List<TenantSummary> tenants, UUID activeTenantId, String activeRole,
                           Set<String> permissions) {}

    private final UserPort users;
    private final MembershipPort memberships;
    private final RefreshTokenPort refreshTokens;
    private final OneTimeTokenPort oneTimeTokens;
    private final RolePermissionPort rolePermissions;
    private final AccessTokenPort accessTokens;
    private final AccountMailPort mail;
    private final RateLimiterPort rateLimiter;
    private final AuditService audit;
    private final PasswordEncoder encoder;
    private final AppProperties props;
    private final Clock clock;
    private final String dummyHash;

    public AuthService(UserPort users, MembershipPort memberships, RefreshTokenPort refreshTokens,
                       OneTimeTokenPort oneTimeTokens, RolePermissionPort rolePermissions, AccessTokenPort accessTokens,
                       AccountMailPort mail, RateLimiterPort rateLimiter, AuditService audit, PasswordEncoder encoder,
                       AppProperties props, Clock clock) {
        this.users = users;
        this.memberships = memberships;
        this.refreshTokens = refreshTokens;
        this.oneTimeTokens = oneTimeTokens;
        this.rolePermissions = rolePermissions;
        this.accessTokens = accessTokens;
        this.mail = mail;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.encoder = encoder;
        this.props = props;
        this.clock = clock;
        this.dummyHash = encoder.encode("dummy-password-for-constant-time");
    }

    // ------------------------------------------------------------------ login

    @Transactional(noRollbackFor = ApiException.class)
    public SessionResult login(String rawEmail, String password) {
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        if (!rateLimiter.allow("login:" + ClientInfo.ip() + ":" + email, props.security().loginRateLimitPerMinute(),
                Duration.ofMinutes(1))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS",
                    "Demasiados intentos. Intenta de nuevo en un minuto.");
        }
        Optional<User> found = users.findByEmail(email);
        if (found.isEmpty()) {
            encoder.matches(password, dummyHash); // igualar tiempos de respuesta
            audit.logAs(null, email, null, "LOGIN_FAILED", "USER", null, false, Map.of("reason", "unknown_user"));
            throw invalidCredentials();
        }
        User user = found.get();
        Instant now = clock.instant();
        if (user.isLocked(now)) {
            audit.logAs(user.id(), email, null, "LOGIN_FAILED", "USER", user.id().toString(), false, Map.of("reason", "locked"));
            throw new ApiException(HttpStatus.LOCKED, "ACCOUNT_LOCKED",
                    "La cuenta está bloqueada temporalmente por intentos fallidos. Intenta más tarde.");
        }
        if (!encoder.matches(password, user.passwordHash())) {
            int attempts = user.failedAttempts() + 1;
            Instant lockUntil = attempts >= props.security().maxFailedAttempts()
                    ? now.plus(props.security().lockDuration()) : null;
            users.recordLoginFailure(user.id(), attempts, lockUntil);
            audit.logAs(user.id(), email, null, "LOGIN_FAILED", "USER", user.id().toString(), false,
                    Map.of("reason", "bad_password", "locked", lockUntil != null));
            throw invalidCredentials();
        }
        if (user.status() == UserStatus.DISABLED) {
            audit.logAs(user.id(), email, null, "LOGIN_FAILED", "USER", user.id().toString(), false, Map.of("reason", "disabled"));
            throw ApiException.forbidden("USER_DISABLED", "Tu usuario está desactivado. Contacta a la administración.");
        }
        users.recordLoginSuccess(user.id(), now);
        audit.logAs(user.id(), email, null, "LOGIN_SUCCESS", "USER", user.id().toString(), true, Map.of());
        return buildSession(user, newRefreshToken(user.id()));
    }

    // ---------------------------------------------------------------- refresh

    @Transactional(noRollbackFor = ApiException.class)
    public SessionResult refresh(String rawRefreshToken) {
        Instant now = clock.instant();
        RefreshToken stored = refreshTokens.findByHash(Tokens.sha256Hex(rawRefreshToken))
                .orElseThrow(AuthService::invalidRefresh);
        if (stored.revokedAt() != null) {
            // Reutilización de un token ya rotado: se asume robo y se revoca toda la familia del usuario.
            refreshTokens.revokeAllForUser(stored.userId(), now);
            audit.logAs(stored.userId(), null, null, "REFRESH_TOKEN_REUSE", "USER", stored.userId().toString(), false, Map.of());
            throw invalidRefresh();
        }
        if (!stored.expiresAt().isAfter(now)) {
            throw invalidRefresh();
        }
        User user = users.findById(stored.userId()).filter(u -> u.status() == UserStatus.ACTIVE)
                .orElseThrow(AuthService::invalidRefresh);
        String newRaw = Tokens.newOpaqueToken();
        UUID newId = refreshTokens.save(user.id(), Tokens.sha256Hex(newRaw), now.plus(props.jwt().refreshExpiration()),
                ClientInfo.ip(), ClientInfo.userAgent());
        refreshTokens.revoke(stored.id(), newId, now);
        SessionResult session = buildSession(user, newRaw);
        if (session.activeTenantId() != null) {
            memberships.touchActivity(user.id(), session.activeTenantId());
        }
        return session;
    }

    // ----------------------------------------------------------------- logout

    @Transactional
    public void logout(String rawRefreshToken) {
        Instant now = clock.instant();
        refreshTokens.findByHash(Tokens.sha256Hex(rawRefreshToken)).ifPresent(rt -> {
            refreshTokens.revoke(rt.id(), null, now);
            audit.logAs(rt.userId(), null, null, "LOGOUT", "USER", rt.userId().toString(), true, Map.of());
            CurrentUser.get().filter(AuthenticatedUser::hasTenant).ifPresent(u ->
                    memberships.addHistory(u.userId(), u.tenantId(), AccessEvent.LEFT, u.roleCode(), u.userId(), ClientInfo.ip()));
        });
    }

    // ---------------------------------------------------------- select tenant

    @Transactional
    public TenantTokenResult selectTenant(UUID tenantId) {
        AuthenticatedUser me = CurrentUser.require();
        Optional<TenantAccess> found = memberships.findActiveAccess(me.userId(), tenantId);
        if (found.isEmpty()) {
            audit.log(null, "TENANT_SWITCH_DENIED", "TENANT", tenantId.toString(), false, Map.of());
            throw ApiException.forbidden("TENANT_ACCESS_DENIED", "No tienes acceso a esta copropiedad.");
        }
        TenantAccess access = found.get();
        if (me.hasTenant() && !me.tenantId().equals(tenantId)) {
            memberships.addHistory(me.userId(), me.tenantId(), AccessEvent.LEFT, me.roleCode(), me.userId(), ClientInfo.ip());
        }
        memberships.addHistory(me.userId(), tenantId, AccessEvent.ENTERED, access.roleCode(), me.userId(), ClientInfo.ip());
        memberships.touchActivity(me.userId(), tenantId);
        users.setLastTenant(me.userId(), tenantId);
        audit.log(tenantId, "TENANT_SWITCH", "TENANT", tenantId.toString(), true, Map.of("role", access.roleCode()));
        return new TenantTokenResult(accessTokens.issue(me.userId(), tenantId), "Bearer", accessTokens.expiresInSeconds(),
                tenantId, access.tenantName(), access.roleCode(),
                rolePermissions.effective(access.schemaName(), access.roleCode()));
    }

    // -------------------------------------------------------------------- me

    public MeResult me() {
        AuthenticatedUser u = CurrentUser.require();
        List<TenantSummary> tenants = memberships.listActiveAccesses(u.userId()).stream().map(AuthService::summary).toList();
        return new MeResult(new UserView(u.userId(), u.email(), u.fullName(), u.platformRole()), tenants, u.tenantId(),
                u.roleCode(), u.permissions());
    }

    // ------------------------------------------------------------- passwords

    @Transactional
    public void changePassword(String currentPassword, String newPassword) {
        AuthenticatedUser me = CurrentUser.require();
        User user = users.findById(me.userId()).orElseThrow(() -> ApiException.unauthorized("UNAUTHENTICATED", "Sesión inválida."));
        if (!encoder.matches(currentPassword, user.passwordHash())) {
            audit.log(me.tenantId(), "PASSWORD_CHANGE_FAILED", "USER", user.id().toString(), false, Map.of());
            throw ApiException.badRequest("INVALID_CURRENT_PASSWORD", "La contraseña actual no es correcta.");
        }
        PasswordPolicy.validate(newPassword);
        if (encoder.matches(newPassword, user.passwordHash())) {
            throw ApiException.badRequest("PASSWORD_UNCHANGED", "La nueva contraseña debe ser distinta de la actual.");
        }
        users.updatePassword(user.id(), encoder.encode(newPassword));
        refreshTokens.revokeAllForUser(user.id(), clock.instant());
        audit.log(me.tenantId(), "PASSWORD_CHANGED", "USER", user.id().toString(), true, Map.of());
    }

    @Transactional
    public void forgotPassword(String rawEmail) {
        if (!rateLimiter.allow("forgot:" + ClientInfo.ip(), 5, Duration.ofMinutes(1))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS", "Demasiados intentos. Intenta más tarde.");
        }
        // Respuesta idéntica exista o no el usuario (evita enumeración de cuentas).
        users.findByEmail(rawEmail.trim().toLowerCase(Locale.ROOT)).filter(u -> u.status() != UserStatus.DISABLED).ifPresent(u -> {
            String raw = Tokens.newOpaqueToken();
            oneTimeTokens.create(u.id(), "PASSWORD_RESET", Tokens.sha256Hex(raw), clock.instant().plus(props.security().resetTokenTtl()));
            mail.sendPasswordReset(u.email(), u.fullName(), raw);
            audit.logAs(u.id(), u.email(), null, "PASSWORD_RESET_REQUESTED", "USER", u.id().toString(), true, Map.of());
        });
    }

    /** Sirve para recuperación de contraseña y para activar una invitación. */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        PasswordPolicy.validate(newPassword);
        Instant now = clock.instant();
        OneTimeToken token = oneTimeTokens.findUsable(Tokens.sha256Hex(rawToken), now).orElseThrow(() ->
                ApiException.badRequest("INVALID_TOKEN", "El enlace no es válido o ya expiró."));
        User user = users.findById(token.userId()).orElseThrow(() ->
                ApiException.badRequest("INVALID_TOKEN", "El enlace no es válido o ya expiró."));
        if (user.status() == UserStatus.DISABLED) {
            throw ApiException.forbidden("USER_DISABLED", "Tu usuario está desactivado. Contacta a la administración.");
        }
        String hash = encoder.encode(newPassword);
        if (user.status() == UserStatus.INVITED) {
            users.activateWithPassword(user.id(), hash);
        } else {
            users.updatePassword(user.id(), hash);
        }
        oneTimeTokens.markUsed(token.id(), now);
        refreshTokens.revokeAllForUser(user.id(), now);
        audit.logAs(user.id(), user.email(), null, "PASSWORD_RESET_COMPLETED", "USER", user.id().toString(), true,
                Map.of("purpose", token.purpose()));
    }

    // --------------------------------------------------------------- helpers

    private String newRefreshToken(UUID userId) {
        String raw = Tokens.newOpaqueToken();
        refreshTokens.save(userId, Tokens.sha256Hex(raw), clock.instant().plus(props.jwt().refreshExpiration()),
                ClientInfo.ip(), ClientInfo.userAgent());
        return raw;
    }

    private SessionResult buildSession(User user, String rawRefresh) {
        List<TenantAccess> accesses = memberships.listActiveAccesses(user.id());
        TenantAccess active = pickActive(user, accesses);
        UUID activeId = active == null ? null : active.tenantId();
        Set<String> permissions = active == null ? Set.of() : rolePermissions.effective(active.schemaName(), active.roleCode());
        return new SessionResult(accessTokens.issue(user.id(), activeId), rawRefresh, "Bearer", accessTokens.expiresInSeconds(),
                new UserView(user.id(), user.email(), user.fullName(), user.platformRole()),
                accesses.stream().map(AuthService::summary).toList(), activeId,
                active == null ? null : active.roleCode(), permissions);
    }

    private static TenantAccess pickActive(User user, List<TenantAccess> accesses) {
        if (user.lastTenantId() != null) {
            for (TenantAccess a : accesses) {
                if (a.tenantId().equals(user.lastTenantId())) return a;
            }
        }
        return accesses.size() == 1 ? accesses.get(0) : null;
    }

    private static TenantSummary summary(TenantAccess a) {
        return new TenantSummary(a.tenantId(), a.tenantName(), a.slug(), a.roleCode());
    }

    private static ApiException invalidCredentials() {
        return ApiException.unauthorized("INVALID_CREDENTIALS", "Correo o contraseña incorrectos.");
    }

    private static ApiException invalidRefresh() {
        return ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Tu sesión expiró. Inicia sesión nuevamente.");
    }
}
