package com.codevam.vecindad.identity.adapter.out.security;

import com.codevam.vecindad.identity.application.port.out.MembershipPort;
import com.codevam.vecindad.identity.application.port.out.RolePermissionPort;
import com.codevam.vecindad.identity.application.port.out.UserPort;
import com.codevam.vecindad.identity.domain.TenantAccess;
import com.codevam.vecindad.identity.domain.User;
import com.codevam.vecindad.identity.domain.UserStatus;
import com.codevam.vecindad.shared.security.AuthenticatedUser;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import com.codevam.vecindad.shared.tenancy.TenantRef;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Autentica el JWT y, si trae tenant, verifica EN BD que el usuario tenga una membresía activa.
 * El tenant activo solo se fija aquí; nunca se lee de headers, parámetros ni cuerpo.
 * Este filtro NO es un bean (se instancia en SecurityConfig) para que Boot no lo registre dos veces.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    public static final String TENANT_ACCESS_REVOKED_ATTR = "vecindad.tenantAccessRevoked";

    private final JwtService jwt;
    private final UserPort users;
    private final MembershipPort memberships;
    private final RolePermissionPort rolePermissions;

    public JwtAuthenticationFilter(JwtService jwt, UserPort users, MembershipPort memberships,
                                   RolePermissionPort rolePermissions) {
        this.jwt = jwt;
        this.users = users;
        this.memberships = memberships;
        this.rolePermissions = rolePermissions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        try {
            authenticate(req);
            chain.doFilter(req, res);
        } finally {
            TenantContext.clear();
        }
    }

    private void authenticate(HttpServletRequest req) {
        String header = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return;
        }
        Optional<JwtService.Parsed> parsed = jwt.parse(header.substring(7).trim());
        if (parsed.isEmpty()) {
            return;
        }
        Optional<User> found = users.findById(parsed.get().userId()).filter(u -> u.status() == UserStatus.ACTIVE);
        if (found.isEmpty()) {
            return;
        }
        User user = found.get();
        UUID tenantId = parsed.get().tenantId();
        TenantAccess access = null;
        if (tenantId != null) {
            access = memberships.findActiveAccess(user.id(), tenantId).orElse(null);
            if (access == null) {
                // Token válido pero sin acceso vigente a esa copropiedad: no se autentica.
                req.setAttribute(TENANT_ACCESS_REVOKED_ATTR, Boolean.TRUE);
                return;
            }
        }

        Set<String> permissions = access == null ? Set.of() : rolePermissions.effective(access.schemaName(), access.roleCode());
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (user.platformRole() != null) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + user.platformRole()));
        }
        if (access != null) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + access.roleCode()));
            permissions.forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
        }

        AuthenticatedUser principal = new AuthenticatedUser(user.id(), user.email(), user.fullName(), user.platformRole(),
                access == null ? null : access.tenantId(), access == null ? null : access.schemaName(),
                access == null ? null : access.roleCode(), permissions);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
        if (access != null) {
            TenantContext.set(new TenantRef(access.tenantId(), access.schemaName()));
        }
    }
}
