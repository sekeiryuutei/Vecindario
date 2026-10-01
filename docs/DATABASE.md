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

## Fase 2a (schema de cada tenant, migración V2)
`people` (user_id opcional enlaza con un usuario global) · `unit_relations` (N:N persona-inmueble: OWNER/TENANT/RESIDENT, histórico con
ACTIVE/ENDED) · `vehicle_types` · `vehicle_type_limits` · `unit_vehicle_limits` · `parking_spaces` · `vehicles` (`status` AUTHORIZED/BLOCKED
y `presence` INSIDE/OUTSIDE son conceptos separados) · `vehicle_access_events` (índice único: una salida por entrada) · `security_alerts`.

Acceso a datos: estas tablas se leen/escriben con `TenantJdbc`, que reemplaza `{s}` por el schema del tenant ACTIVO (validado). Las operaciones
críticas son **una sola sentencia SQL** (CTE `UPDATE ... RETURNING` + `INSERT`), por lo que son atómicas frente a solicitudes simultáneas:
entrada/salida de vehículos, modificación/eliminación solo si está fuera, e inserción solo si el inmueble está bajo su límite.
