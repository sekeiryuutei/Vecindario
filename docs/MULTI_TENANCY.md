# Multi-tenancy (schema por copropiedad)

```
public                        tenant_demo_norte            tenant_demo_sur
├── users, tenants ...        ├── property_units           ├── property_units
├── tenant_memberships        ├── role_permission_overrides├── role_permission_overrides
├── audit_logs ...            └── flyway_schema_history    └── flyway_schema_history
```

## Cómo se determina el tenant
1. El access token (JWT) lleva `sub` (usuario) y opcionalmente `tid` (copropiedad activa). **No** lleva rol ni permisos.
2. `JwtAuthenticationFilter` valida firma/expiración, carga el usuario y, si hay `tid`, verifica **en BD** que exista una
   membresía ACTIVA del usuario en esa copropiedad ACTIVA. Solo entonces fija `TenantContext`.
3. `TenantIdentifierResolver` (Hibernate) lee `TenantContext`; `SchemaPerTenantConnectionProvider` hace
   `SET search_path TO "tenant_x", public` al tomar la conexión y `SET search_path TO public` al devolverla.
4. El tenant **nunca** se toma de parámetros, headers ni cuerpo. `?tenantId=` y `X-Tenant-Id` se ignoran (hay test).
5. Defensa en profundidad: los adaptadores de tenant llaman `TenantContext.require()` antes de tocar datos, y todo nombre
   de schema que llega a SQL dinámico pasa por `SchemaNames` (regex estricta `^tenant_[a-z][a-z0-9_]{2,39}$`).

## Cambio de copropiedad
`POST /api/v1/auth/select-tenant {tenantId}` → el backend valida la membresía, registra `LEFT`/`ENTERED` en
`tenant_access_history`, guarda `last_tenant_id` y emite un token nuevo.

## Historial de accesos
`tenant_memberships` (estado actual: ACTIVE/SUSPENDED/REVOKED) + `tenant_access_history` (inmutable por trigger):
GRANTED, ROLE_CHANGED, SUSPENDED, ACTIVATED, REVOKED, ENTERED, LEFT. Las relaciones nunca se borran; una revocación
puede reactivarse conservando el historial.

## Migraciones
- Globales: `db/migration/global`, las aplica Spring Boot/Flyway al arrancar.
- Tenant: `db/migration/tenant`, las aplica `FlywayTenantSchemaAdapter` al crear la copropiedad y `TenantMigrationRunner`
  a **todas** las existentes en cada arranque (idempotente; Flyway lleva un `flyway_schema_history` por schema).
- Crear una copropiedad: fila `PROVISIONING` → crear schema + migrar → `ACTIVE` (o `FAILED` si algo falla).
- Nunca edites una migración ya aplicada: agrega `V<n+1>__…sql`.

## Límites conocidos
- Un fallo de migración en un tenant aborta el arranque (fail-fast, a propósito).
- Con cientos de tenants el arranque migra uno por uno; para miles convendría migrar en paralelo/en un job.
