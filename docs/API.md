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
