package com.codevam.vecindad.identity.domain;

import java.util.Set;

public final class Roles {
    public static final String SUPER_ADMIN_PLATFORM = "SUPER_ADMIN_PLATFORM";
    public static final String ADMINISTRADOR = "ADMINISTRADOR";
    public static final String SECRETARIA_ADMINISTRACION = "SECRETARIA_ADMINISTRACION";
    public static final String CONTADOR = "CONTADOR";
    public static final String CONSEJO = "CONSEJO";
    public static final String PROPIETARIO = "PROPIETARIO";
    public static final String ARRENDATARIO = "ARRENDATARIO";
    public static final String RESIDENTE = "RESIDENTE";
    public static final String PORTERO = "PORTERO";
    public static final String SUPERVISOR_PORTERIA = "SUPERVISOR_PORTERIA";
    public static final String MANTENIMIENTO = "MANTENIMIENTO";
    public static final String PROVEEDOR = "PROVEEDOR";

    /** Roles asignables dentro de una copropiedad (excluye el rol de plataforma). */
    public static final Set<String> TENANT_ROLES = Set.of(
            ADMINISTRADOR, SECRETARIA_ADMINISTRACION, CONTADOR, CONSEJO, PROPIETARIO, ARRENDATARIO,
            RESIDENTE, PORTERO, SUPERVISOR_PORTERIA, MANTENIMIENTO, PROVEEDOR);

    private Roles() {}
}
