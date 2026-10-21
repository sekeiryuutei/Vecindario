# API v1

**Content-Type:** todo cuerpo se envía como `application/json` (incluso un POST sin datos usa `{}`); de lo contrario la API responde 415 `UNSUPPORTED_MEDIA_TYPE`.

Swagger UI: `/swagger-ui.html` · OpenAPI JSON: `/v3/api-docs`. Paginación: `?page=0&size=20` (máx. 100) →
`{content, page, size, totalElements, totalPages}`. Errores: `{code, message, timestamp, path, traceId, details}`.

| Método y ruta | Auth |
|---|---|
| POST `/auth/login`, `/auth/refresh`, `/auth/logout`, `/auth/forgot-password`, `/auth/reset-password` | pública |
| POST `/auth/select-tenant`, `/auth/change-password` · GET `/auth/me` | token |
| GET `/public/config` | pública |
| GET `/public/manifest.webmanifest` | pública (manifest PWA con `APP_NAME`) |
| GET/PUT `/tenants/current` · PUT `/tenants/current/features/{feature}` | miembro / TENANT_SETTINGS_MANAGE (solo canales autogestionables) |
| GET/POST `/tenant-members` · PATCH `/tenant-members/{id}/role` · POST `…/suspend`, `…/activate` · DELETE `…/{id}` · GET `…/{id}/history` | USERS_VIEW / USERS_MANAGE |
| GET `/roles`, `/roles/permissions` · PUT `/roles/{rol}/permissions/{perm}` | ROLES_MANAGE |
| GET/POST `/properties` · GET/PUT/DELETE `/properties/{id}` | PROPERTIES_* |
| GET `/audit` | AUDIT_VIEW (tenant del token) |
| POST/GET `/platform/companies` · POST/GET `/platform/tenants` · PATCH `/platform/tenants/{id}/status` · POST `/platform/tenants/{id}/members` · GET `/platform/audit` | SUPER_ADMIN_PLATFORM |

Códigos de error frecuentes: `INVALID_CREDENTIALS` 401, `ACCOUNT_LOCKED` 423, `TENANT_ACCESS_DENIED` 403,
`TENANT_ACCESS_REVOKED` 403, `TENANT_NOT_SELECTED` 400, `FORBIDDEN` 403, `RESOURCE_NOT_FOUND` 404,
`VALIDATION_ERROR` 400, `WEAK_PASSWORD` 400, `TOO_MANY_REQUESTS` 429.

## Fase 2a
| Método y ruta | Permiso |
|---|---|
| GET/POST `/residents` · GET/PUT/DELETE `/residents/{id}` · PUT `/residents/{id}/user` · GET `/residents/{id}/relations` | RESIDENTES_VIEW/CREATE/UPDATE/DELETE |
| GET/POST `/properties/{unitId}/relations` · POST `/relations/{id}/end` | RESIDENTES_VIEW / RESIDENTES_UPDATE |
| GET `/my/units` | UNITS_VIEW_OWN (solo los inmuebles de la persona enlazada al usuario) |
| GET/POST `/vehicles` · GET/PUT/DELETE `/vehicles/{id}` | VEHICLES_VIEW/CREATE/UPDATE/DELETE |
| GET/POST `/my/vehicles` · PUT/DELETE `/my/vehicles/{id}` | VEHICLES_VIEW_OWN / VEHICLES_MANAGE_OWN |
| GET `/vehicle-types` · PUT `/vehicle-types/{code}` · GET `/vehicle-limits` · PUT `/vehicle-limits/{type}` · PUT `/properties/{unitId}/vehicle-limits/{type}` | VEHICLES_VIEW / VEHICLE_RULES_MANAGE |
| GET/POST `/parking-spaces` | VEHICLES_VIEW / PARKING_MANAGE |
| GET `/access/vehicles/lookup?plate=` · GET `/access/vehicles/inside` · GET `/access/events` | VEHICLES_VIEW |
| POST `/access/entry` · POST `/access/exit` | VEHICLES_REGISTER_ENTRY / VEHICLES_REGISTER_EXIT |
| GET `/access/alerts` · POST `/access/alerts/{id}/resolve` | SECURITY_ALERTS_VIEW |

Errores nuevos: `VEHICLE_INSIDE` 409 (mensaje fijo: "Este vehículo se encuentra actualmente dentro de la copropiedad. Debe registrarse su salida antes de modificar su registro."),
`VEHICLE_ALREADY_INSIDE` 409, `VEHICLE_NOT_INSIDE` 409, `VEHICLE_BLOCKED` 403, `VEHICLE_NOT_REGISTERED` 404, `VEHICLE_LIMIT_REACHED` 409,
`PLATE_ALREADY_REGISTERED` 409, `DOCUMENT_ALREADY_EXISTS` 409, `PERSON_HAS_RELATIONS` 409, `RELATION_ALREADY_EXISTS` 409.

## Fase 2b
| Método y ruta | Permiso |
|---|---|
| POST `/my/visitors/invitations` (devuelve `qrToken` una sola vez) · POST `/my/visitors/invitations/{id}/cancel` · POST `…/{id}/regenerate-qr` | VISITORS_CREATE |
| GET `/my/visitors/invitations` · GET `/my/visitors/requests` | VISITORS_VIEW_OWN |
| POST `/my/visitors/requests/{id}/authorize` · `…/reject` | VISITORS_RESPOND_OWN |
| POST `/visitors/validate-qr` · `/visitors/check-in-qr` · `/visitors/walk-in` · `/visitors/visits/{id}/check-in` · `…/check-out` | VISITORS_AUTHORIZE |
| GET `/visitors/visits` · GET `/visitors/invitations` | VISITORS_VIEW |
| POST/GET `/packages` · GET `/packages/{id}` · POST `/packages/{id}/deliver` · `…/return` | PACKAGES_MANAGE / PACKAGES_VIEW |
| GET `/my/packages` | PACKAGES_VIEW_OWN |
| POST/GET `/incidents` · GET `/incidents/{id}` · PATCH `/incidents/{id}/status` · PUT `/incidents/{id}/assignee` | INCIDENTS_CREATE / INCIDENTS_VIEW / INCIDENTS_MANAGE |
| GET `/security/summary` | SECURITY_SUMMARY_VIEW |

Códigos nuevos: `INVALID_QR` 404, `QR_ALREADY_USED` / `QR_EXPIRED` / `QR_NOT_YET_VALID` / `QR_CANCELLED` 409, `VISIT_PENDING_AUTH` 409,
`VISIT_REJECTED` 403, `VISIT_ALREADY_DECIDED` 409, `VISIT_NOT_INSIDE` 409, `PARCEL_NOT_PENDING` 409, `RESOLUTION_REQUIRED` 400, `USER_NOT_MEMBER` 409.

Flujo sin invitación: portero `POST /visitors/walk-in` → residente ve `GET /my/visitors/requests?status=PENDING_AUTH` → `authorize|reject` → portero `check-in` (solo si AUTHORIZED).

## Fase 3a (cartera)
| Método y ruta | Permiso |
|---|---|
| GET/PUT `/billing/settings` | FINANCE_VIEW / FINANCE_MANAGE |
| POST/GET `/billing/charges` · GET `/billing/charges/{id}` | FINANCE_MANAGE / FINANCE_VIEW |
| POST `/billing/charges/{id}/void` · POST `/billing/charges/{id}/adjustments` (CREDIT/DEBIT) | FINANCE_MANAGE |
| POST `/billing/runs/ordinary` · POST `/billing/runs/interest` | FINANCE_MANAGE |
| GET `/billing/portfolio` · GET `/billing/units/{id}/statement` · GET `/billing/units/{id}/ledger` | FINANCE_VIEW |
| POST `/payments` (header `Idempotency-Key`) | PAYMENTS_CREATE |
| GET `/payments`, `/payments/{id}` · POST `/payments/{id}/reverse` | FINANCE_VIEW / FINANCE_MANAGE |
| GET `/my/statement?unitId=` | FINANCE_VIEW_OWN (propietario por defecto; configurable por copropiedad) |

POST `/payments`: 201 la primera vez, **200** con el mismo pago si se repite la `Idempotency-Key` (campo `replayed: true`), 409 `IDEMPOTENCY_KEY_REUSED` si la llave se reutiliza con otro monto/inmueble.
Códigos: `CHARGE_ALREADY_EXISTS`, `CHARGE_HAS_PAYMENTS`, `CHARGE_ALREADY_VOIDED`, `ADJUSTMENT_NOT_ALLOWED`, `PAYMENT_ALREADY_REVERSED`, `INTEREST_DISABLED`, `INVALID_AMOUNT`, `INVALID_PAYMENT_DATE`, `INVALID_RUN`, `INVALID_ALLOCATION_ORDER`.

## Fase 3b (conciliación bancaria)
| Método y ruta | Permiso |
|---|---|
| POST `/bank-reconciliation/imports` (multipart: `file`, `bankName`, `accountLabel`, `profileId?`) → 202 | PAYMENTS_RECONCILE |
| GET `/bank-reconciliation/imports` · GET `/bank-reconciliation/imports/{id}` | FINANCE_VIEW |
| GET `/bank-reconciliation/rows?importId&status` · GET `/bank-reconciliation/rows/{id}` (candidatos + decisiones) | FINANCE_VIEW |
| POST `/bank-reconciliation/rows/{id}/match` · `/reject` · `/revert` | PAYMENTS_RECONCILE |
| GET/PUT `/bank-reconciliation/settings` | FINANCE_VIEW / FINANCE_MANAGE |
| GET/POST `/bank-reconciliation/profiles` · PUT/DELETE `/bank-reconciliation/profiles/{id}` | FINANCE_VIEW / FINANCE_MANAGE |

Estados de importación: `QUEUED → PROCESSING → COMPLETED | FAILED`. Estados de fila: `AUTO_MATCHED`, `MANUAL_REVIEW`, `MATCHED`, `REJECTED`, `DUPLICATE`, `AMBIGUOUS`, `IGNORED`.
Códigos: `IMPORT_ALREADY_EXISTS` 409, `ROW_NOT_PENDING` 409, `ROW_NOT_APPLIED` 409, `UNSUPPORTED_FILE_TYPE` 400, `FILE_TOO_LARGE` 413, `IMPORT_QUEUE_FULL` 429, `INVALID_THRESHOLD` 400, `INVALID_PROFILE` 400, `PROFILE_NAME_EXISTS` 409.

## Fase 3c (Wompi y Certificados PDF)
| Método y ruta | Permiso / Auth |
|---|---|
| POST `/my/payments/online` · GET `/my/payments/online/{reference}` | FINANCE_VIEW_OWN (crea y consulta intenciones Wompi; mínimo `$1.500 COP`) |
| POST `/public/wompi/events` | Pública (valida checksum SHA-256 e idempotencia) |
| GET `/public/certificates/verify/{code}` | Pública (validador de autenticidad de certificados PDF emitidos) |
| GET `/my/certificates/paz-y-salvo` · `/statement` · `/ownership` · `/history` | FINANCE_VIEW_OWN / UNITS_VIEW_OWN (PDFs OpenPDF firmados digitalmente con hash) |
| GET `/certificates/units/{unitId}/paz-y-salvo` · `/statement` · `/ownership` · `/history` | FINANCE_VIEW (certificados administrativos) |
| PUT `/platform/tenants/{id}/features` | SUPER_ADMIN_PLATFORM (activa/desactiva feature flags por copropiedad) |

Códigos: `AMOUNT_TOO_LOW` 400 (mínimo Wompi), `INVALID_SIGNATURE` 400/401, `ONLINE_PAYMENTS_DISABLED` 403,
`INTENT_NOT_PENDING` 409, `AMOUNT_MISMATCH` / `CURRENCY_MISMATCH` 400.

## Fase 4 (Zonas Comunes, Reservas, PQRS, Comunicados, Notificaciones, Documentos, Asambleas)
| Método y ruta | Permiso / Auth |
|---|---|
| GET/POST `/common-areas` · GET/PUT/DELETE `/common-areas/{id}` | COMMON_AREAS_MANAGE / RESERVATIONS_VIEW |
| GET `/common-areas/{id}/availability?date=` | RESERVATIONS_VIEW / RESERVATIONS_VIEW_OWN |
| POST/GET `/my/reservations` · POST `/my/reservations/{id}/cancel` | RESERVATIONS_MANAGE_OWN / RESERVATIONS_VIEW_OWN |
| GET `/reservations` · POST `/reservations/{id}/approve` · `/reject` | RESERVATIONS_MANAGE |
| POST/GET `/my/pqrs` · GET `/my/pqrs/{id}` · POST `/my/pqrs/{id}/messages` | PQR_VIEW_OWN |
| GET `/pqrs` · GET `/pqrs/{id}` · POST `/pqrs/{id}/messages` · PATCH `/pqrs/{id}/status` | PQR_MANAGE |
| GET `/pqrs/metrics/sla` | PQR_MANAGE |
| POST/GET `/communications` · PATCH `/communications/{id}/publish` · `/archive` | COMMUNICATIONS_MANAGE |
| GET `/my/communications` · POST `/my/communications/{id}/read` | COMMUNICATIONS_VIEW |
| GET/PUT `/notifications/settings` | NOTIFICATIONS_MANAGE |
| POST `/notifications/portfolio-reminders` | NOTIFICATIONS_MANAGE |
| GET `/notifications/logs` | NOTIFICATIONS_MANAGE |
| POST `/documents` (multipart: file, title, category, minimumRole) | DOCUMENTS_MANAGE |
| GET `/documents` · DELETE `/documents/{id}` | DOCUMENTS_MANAGE |
| GET `/my/documents` · GET `/my/documents/{id}/download` | DOCUMENTS_VIEW |
| POST/GET `/assemblies` · GET/PATCH `/assemblies/{id}/status` | ASSEMBLIES_MANAGE / ASSEMBLIES_VIEW |
| POST `/assemblies/{id}/check-in` · GET `/assemblies/{id}/quorum` | ASSEMBLIES_MANAGE / ASSEMBLIES_VIEW |
| POST `/assemblies/{id}/topics` · GET `/assemblies/{id}/topics` | ASSEMBLIES_MANAGE / ASSEMBLIES_VIEW |
| POST `/assemblies/topics/{id}/open-vote` · `/close-vote` | ASSEMBLIES_MANAGE |
| GET `/assemblies/topics/{id}/results` | ASSEMBLIES_VIEW |
| POST `/my/assemblies/topics/{id}/vote` | ASSEMBLIES_VOTE (ponderación por coeficiente Ley 675) |

## Fase 5 (Proveedores, Mantenimiento, Presupuestos, Reportes y Cámaras LPR)
| Método y ruta | Permiso |
|---|---|
| GET/POST `/suppliers` · GET/PUT/DELETE `/suppliers/{id}` | SUPPLIERS_VIEW / SUPPLIERS_MANAGE |
| GET/POST `/suppliers/{id}/contracts` | SUPPLIERS_VIEW / SUPPLIERS_MANAGE |
| GET/POST `/maintenance/assets` · GET/PUT `/maintenance/assets/{id}` | MAINTENANCE_VIEW / MAINTENANCE_MANAGE |
| GET/POST `/maintenance/plans` · GET `/maintenance/plans/{id}` | MAINTENANCE_VIEW / MAINTENANCE_MANAGE |
| GET/POST `/maintenance/work-orders` · GET `/maintenance/work-orders/{id}` | MAINTENANCE_VIEW / MAINTENANCE_MANAGE |
| PATCH/PUT `/maintenance/work-orders/{id}/status` | MAINTENANCE_MANAGE |
| GET/POST `/budgets` · GET `/budgets/{id}` | BUDGET_VIEW / BUDGET_MANAGE |
| GET/POST `/budgets/{id}/items` · GET `/budgets/items/{itemId}` | BUDGET_VIEW / BUDGET_MANAGE |
| POST/GET `/budgets/items/{itemId}/executions` | BUDGET_MANAGE / BUDGET_VIEW |
| GET `/budgets/{year}/summary` | BUDGET_VIEW |
| GET `/reports/portfolio` · `/reports/budget/{year}` · `/reports/maintenance` | REPORTS_VIEW |
| GET `/reports/portfolio/export/csv` · `/reports/portfolio/export/pdf` | REPORTS_EXPORT |
| GET `/cameras/status` · POST `/cameras/detect` | VEHICLES_VIEW / VEHICLES_MANAGE |

## Fase 6/7 (Autogestión, Nómina y WhatsApp)
| Método y ruta | Permiso / Auth |
|---|---|
| GET/PUT `/tenants/current` | Miembro / TENANT_SETTINGS_MANAGE |
| PUT `/tenants/current/features/{feature}` | TENANT_SETTINGS_MANAGE; solo `WHATSAPP`, `CAMERAS`, `LPR` y `ASSEMBLIES` |
| GET/PUT `/payroll/settings` | PAYROLL_VIEW / PAYROLL_MANAGE |
| GET/POST `/payroll/employees` · GET/PUT/DELETE `/payroll/employees/{id}` | PAYROLL_VIEW / PAYROLL_MANAGE |
| GET/POST `/payroll/runs` · GET `/payroll/runs/{id}` | PAYROLL_VIEW / PAYROLL_MANAGE |
| POST `/payroll/runs/{id}/recalculate` · `/pay` · `/void` | PAYROLL_MANAGE |
| GET `/payroll/runs/{id}/export.csv` | PAYROLL_VIEW (`Accept: text/csv`) |
| GET `/public/whatsapp/webhook` | Pública (verificación de Meta Cloud API) |
| POST `/public/whatsapp/webhook` | Pública (firma `X-Hub-Signature-256` e idempotencia) |

La corrida de nómina usa numeración versionada por periodo (`NOM-AAAA-MM`, `NOM-AAAA-MM-2`, …) para permitir regenerar
un periodo anulado. El webhook de WhatsApp resuelve teléfono → persona → copropiedad, exige el feature `WHATSAPP` y responde
por el asistente configurado; si el proveedor remoto falla, usa el respaldo local.

## Panel general e integraciones por copropiedad
| Método y ruta | Permiso / Auth |
|---|---|
| GET `/my/tenant-overview` | Miembro (resumen de todas las copropiedades asignadas) |
| GET `/platform/tenants/{tenantId}/integrations` | SUPER_ADMIN_PLATFORM (estado enmascarado) |
| GET `/platform/online-payments/summary` | SUPER_ADMIN_PLATFORM (cobros, tarifa de plataforma y comisión de la pasarela) |
| PUT `/platform/tenants/{tenantId}/integrations/wompi` | SUPER_ADMIN_PLATFORM |
| PUT `/platform/tenants/{tenantId}/integrations/ai` | SUPER_ADMIN_PLATFORM |
| DELETE `/platform/tenants/{tenantId}/integrations/{WOMPI\|AI}` | SUPER_ADMIN_PLATFORM (vuelve a los valores globales) |

`GET /my/tenant-overview` devuelve, por cada copropiedad en la que el usuario tiene relación, el rol, si puede
administrarla y un bloque de métricas (inmuebles, personas, PQRS abiertas, incidentes abiertos, reservas próximas,
paquetes pendientes y cartera en mora con su importe). El frontend lo usa como panel general antes de elegir
copropiedad.

`GET /my/payments/online/quote?amount=` cotiza un pago en línea: devuelve `netAmountInCents` (deuda), `platformFeeInCents`
(tarifa de la plataforma), `commissionInCents` (comisión estimada de la pasarela) y `totalInCents` (lo que se cobra). La
creación de la intención (`POST /my/payments/online` y `POST /my/reservations/{id}/checkout`) devuelve los mismos valores
en `netAmountInCents`, `platformFeeInCents` y `commissionInCents`; `amountInCents` es el total cobrado.

Las integraciones por copropiedad guardan las credenciales de Wompi y del proveedor de IA cifradas con AES-GCM
(`INTEGRATIONS_ENCRYPTION_KEY`); la API nunca devuelve llaves privadas ni secretos. La llave pública de Wompi sí se
muestra porque viaja en la URL de checkout. Si la copropiedad no tiene configuración propia se usan los valores
globales del entorno, de modo que una instalación existente sigue funcionando sin cambios.



