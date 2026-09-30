package com.codevam.vecindad.shared.tenancy;

import com.codevam.vecindad.shared.error.ApiException;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Contexto de tenant del hilo actual. Solo lo establece el filtro de autenticación
 * (a partir del JWT + membresía verificada en BD) o procesos internos (seed, migraciones).
 * NUNCA se llena desde parámetros, headers o cuerpo de la petición.
 */
public final class TenantContext {
    private static final ThreadLocal<TenantRef> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(TenantRef ref) {
        SchemaNames.requireTenantSchema(ref.schema());
        CURRENT.set(ref);
    }

    public static Optional<TenantRef> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static Optional<String> currentSchema() {
        return current().map(TenantRef::schema);
    }

    public static TenantRef require() {
        return current().orElseThrow(() ->
                ApiException.badRequest("TENANT_NOT_SELECTED", "Debes seleccionar una copropiedad para continuar."));
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static <T> T callAs(TenantRef ref, Supplier<T> action) {
        TenantRef previous = CURRENT.get();
        set(ref);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static void runAs(TenantRef ref, Runnable action) {
        callAs(ref, () -> {
            action.run();
            return null;
        });
    }
}
