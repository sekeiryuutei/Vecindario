# Hoja de ruta por fases

Cada fase se entrega como ZIP verificable. Las fases posteriores agregan migraciones nuevas (`V2__…`, `V3__…`) en
`db/migration/global` y `db/migration/tenant`; `TenantMigrationRunner` las aplica a todas las copropiedades existentes.

| Fase | Contenido | Estado |
|---|---|---|
| 1 | Infra Docker, multi-tenancy schema-per-tenant, Flyway, JWT + refresh, RBAC, auditoría, membresías con historial, super admin, inmuebles, Swagger, seed, tests de aislamiento | **Entregada (sin ejecutar)** |
| 2a | Personas (N:N), propietarios/arrendatarios/residentes, parqueaderos, tipos y límites de vehículos, regla "vehículo dentro", entradas/salidas, alertas, vista del residente | **Entregada (sin ejecutar)** |
| 2b | Visitantes con invitación/QR y autorización en tiempo real, paquetería, novedades de portería, reportes de seguridad | Pendiente |
| 3 | Cartera, imputación configurable, pagos, conciliación bancaria (CSV/XLSX, matching con puntaje), Wompi, certificados | Pendiente |
| 4 | Zonas comunes y reservas (concurrencia), PQRS/convivencia con SLA, notificaciones (email/push/WhatsApp), comunicados, documentos (S3/MinIO) | Pendiente |
| 5 | Mantenimiento, proveedores, asambleas, presupuesto, reportes, cámaras/LPR (adapters), frontend Angular+Ionic+Capacitor+PWA, E2E Playwright, CI/CD | Pendiente |

## Pendientes conocidos de la Fase 1
- Envío real de correo (hoy `LoggingAccountMailAdapter`; el SMTP llega con notificaciones).
- Adaptador S3/MinIO (`StoragePort`): la infraestructura MinIO ya corre, el código llega con documentos.
- Caché de permisos por rol (hoy se consultan por petición; correcto pero mejorable).
- Endpoint para editar datos de la copropiedad (`TENANT_SETTINGS_MANAGE`).
- Logging JSON estructurado (hoy: patrón con `traceId`).
