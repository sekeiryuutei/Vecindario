# Base de datos

## Schema `public`
`administrator_companies` → `tenants` (slug, schema_name únicos; estado PROVISIONING/ACTIVE/SUSPENDED/FAILED; moneda COP,
zona America/Bogota, locale es-CO; `settings` JSONB solo para configuración variable) → `tenant_features` (feature flags).
`users` (email único case-insensitive) · `roles` · `permissions` · `role_permissions` · `tenant_memberships` (único
usuario+tenant) · `tenant_access_history` · `refresh_tokens` · `one_time_tokens` · `audit_logs`.

## Schema de cada tenant
`property_units` (tipo, identificador único entre activos, coeficiente NUMERIC(9,6), área NUMERIC(10,2), soft delete) ·
`role_permission_overrides`. Dinero: siempre NUMERIC/BigDecimal, nunca float.

Convenciones: UUID como PK, `timestamptz` en UTC, CHECK para enums, índices parciales para soft delete, sin JSONB para
datos que deben ser relacionales.
