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

## Fase 2b (schema de cada tenant, migración V3)
`visitor_invitations` (QR guardado solo como hash SHA-256; `max_entries`/`used_count`) · `visits` (INVITATION/WALK_IN; PENDING_AUTH → AUTHORIZED|REJECTED → INSIDE → LEFT)
· `packages` (RECEIVED/NOTIFIED/DELIVERED/RETURNED) · `incidents` (OPEN/IN_REVIEW/CLOSED). Alertas de visitantes (`INVALID_QR`, `VISITOR_EXPIRED`) reutilizan `security_alerts`.
El ingreso por QR es una sola sentencia (CTE `UPDATE visitor_invitations ... RETURNING` + `INSERT visits`), atómica frente a lecturas simultáneas del mismo código.

## Fase 3a (schema de cada tenant, migración V4)
`billing_settings` (fila única) · `charges` (NUMERIC(14,2); `CHECK amount + adjusted_amount >= paid_amount` impide sobrepagar a nivel de BD; índice único parcial de cuota
ordinaria por inmueble+periodo) · `payments` (`idempotency_key` único parcial; `unapplied_amount` = saldo a favor) · `payment_allocations` · `ledger_entries` (inmutable por trigger).
Dinero siempre NUMERIC/BigDecimal. Global V5 agrega el permiso `FINANCE_VIEW_OWN`.

## Fase 3b (schema de cada tenant, migración V5)
`reconciliation_settings` · `bank_import_profiles` (mapeo de columnas por banco; alias separados por coma) · `bank_statement_imports` (índice único del hash del archivo, salvo FAILED) ·
`bank_statement_rows` (`tx_hash` único entre filas no duplicadas: identidad estable de la transacción) · `bank_row_candidates` · `bank_row_decisions` (inmutable por trigger) · `bank_counterparty_hints` (aprendizaje de pagadores).

## Fase 3c (migración V6)
- Global `public`: `online_payment_intents` (referencia única, estado PENDING/APPROVED/DECLINED/VOIDED/ERROR, tenant_id, monto, datos de transacción Wompi).
- Tenant `{s}`: `issued_certificates` (número único, código alfanumérico público de verificación, tipo PAZ_Y_SALVO/ESTADO_CUENTA/PROPIEDAD, SHA-256 de integridad).

## Fase 4 (migraciones V7 y V8)
- Global `public` (V7 y V8): Permisos `COMMON_AREAS_MANAGE`, `RESERVATIONS_VIEW_OWN`, `RESERVATIONS_MANAGE_OWN`, `COMMUNICATIONS_VIEW`, `COMMUNICATIONS_MANAGE`, `PQR_VIEW_OWN`, `PQR_MANAGE`, `ASSEMBLIES_VIEW`, `ASSEMBLIES_MANAGE`, `ASSEMBLIES_VOTE`, `NOTIFICATIONS_MANAGE`, `DOCUMENTS_VIEW`, `DOCUMENTS_MANAGE`.
- Tenant `{s}` (V7):
  - `common_areas`: aforo, tarifas por bloque, depósitos, horarios de apertura/cierre, restricciones de anticipación y cancelación.
  - `reservations`: estados PENDING_APPROVAL/APPROVED/REJECTED/CANCELLED/COMPLETED, métodos de pago, verificación de traslape e integridad de mora.
  - `pqr_ticket_seq` & `pqrs`: secuencia `PQR-YYYY-XXXX`, categorías con SLA (24h para CONVIVENCIA_RUIDO), estados.
  - `pqrs_messages`: conversación con bandera `is_internal` para notas privadas entre administradores.
  - `communications`: comunicados segmentados (población ALL/PROPIETARIOS/RESIDENTES/TORRE), fijados (`is_pinned`), fecha de expiración.
  - `communication_reads`: acuse de lectura por usuario/persona.
  - `tenant_notification_settings`: habilitación por canal (EMAIL/PUSH/WHATSAPP) y plantillas por evento.
  - `notification_logs`: trazabilidad histórica de envíos por canal con estado y destinatario.
  - `documents`: repositorio digital con categorías y filtro de seguridad por jerarquía de rol mínimo (`minimum_role`).
  - `assemblies`: asambleas ordinarias y extraordinarias, quórum requerido, ubicación o enlace virtual.
  - `assembly_attendances`: registro de asistencia presencial o por poder (`is_proxy`, `proxy_name`).
  - `assembly_topics`: temas con opciones JSONB y tipo de votación NOMINAL o COEFFICIENT (ponderada Ley 675).
  - `assembly_votes`: votos únicos por inmueble (`uq_assembly_votes_topic_unit`) con peso según coeficiente de propiedad horizontal.

