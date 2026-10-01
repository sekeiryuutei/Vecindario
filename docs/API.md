# API v1

Swagger UI: `/swagger-ui.html` · OpenAPI JSON: `/v3/api-docs`. Paginación: `?page=0&size=20` (máx. 100) →
`{content, page, size, totalElements, totalPages}`. Errores: `{code, message, timestamp, path, traceId, details}`.

| Método y ruta | Auth |
|---|---|
| POST `/auth/login`, `/auth/refresh`, `/auth/logout`, `/auth/forgot-password`, `/auth/reset-password` | pública |
| POST `/auth/select-tenant`, `/auth/change-password` · GET `/auth/me` | token |
| GET `/public/config` | pública |
| GET `/tenants/current` | miembro |
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
