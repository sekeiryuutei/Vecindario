package com.codevam.vecindad.shared.security;

import com.codevam.vecindad.shared.error.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public final class CurrentUser {
    private CurrentUser() {}

    public static Optional<AuthenticatedUser> get() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof AuthenticatedUser u) {
            return Optional.of(u);
        }
        return Optional.empty();
    }

    public static AuthenticatedUser require() {
        return get().orElseThrow(() ->
                ApiException.unauthorized("UNAUTHENTICATED", "Debes iniciar sesión para continuar."));
    }

    public static AuthenticatedUser requireTenant() {
        AuthenticatedUser u = require();
        if (!u.hasTenant()) {
            throw ApiException.badRequest("TENANT_NOT_SELECTED", "Debes seleccionar una copropiedad para continuar.");
        }
        return u;
    }
}
