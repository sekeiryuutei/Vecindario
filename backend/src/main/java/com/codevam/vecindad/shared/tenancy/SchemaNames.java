package com.codevam.vecindad.shared.tenancy;

import java.util.regex.Pattern;

/** Validación estricta de nombres de schema. Todo nombre que llegue a SQL dinámico pasa por aquí. */
public final class SchemaNames {
    public static final String PUBLIC = "public";
    private static final Pattern TENANT_SCHEMA = Pattern.compile("^tenant_[a-z][a-z0-9_]{2,39}$");
    private static final Pattern SLUG = Pattern.compile("^[a-z][a-z0-9_]{2,39}$");

    private SchemaNames() {}

    public static boolean isTenantSchema(String s) {
        return s != null && TENANT_SCHEMA.matcher(s).matches();
    }

    public static String requireTenantSchema(String s) {
        if (!isTenantSchema(s)) {
            throw new IllegalArgumentException("Nombre de schema de tenant inválido");
        }
        return s;
    }

    /** Acepta "public" o un schema de tenant válido. */
    public static String requireKnown(String s) {
        return PUBLIC.equals(s) ? s : requireTenantSchema(s);
    }

    public static boolean isValidSlug(String slug) {
        return slug != null && SLUG.matcher(slug).matches();
    }

    public static String fromSlug(String slug) {
        if (!isValidSlug(slug)) {
            throw new IllegalArgumentException("Slug de copropiedad inválido");
        }
        return "tenant_" + slug;
    }

    /** Devuelve el identificador entre comillas dobles, ya validado. */
    public static String quoted(String schema) {
        return "\"" + requireKnown(schema) + "\"";
    }
}
