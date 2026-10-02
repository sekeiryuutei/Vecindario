# Hoja de ruta por fases

Cada fase se entrega como ZIP verificable. Las fases posteriores agregan migraciones nuevas (`V2__…`, `V3__…`) en
`db/migration/global` y `db/migration/tenant`; `TenantMigrationRunner` las aplica a todas las copropiedades existentes.

| Fase | Contenido | Estado |
|---|---|---|
| 1 | Infra Docker, multi-tenancy schema-per-tenant, Flyway, JWT + refresh, RBAC, auditoría, membresías con historial, super admin, inmuebles, Swagger, seed, tests de aislamiento | **Entregada (sin ejecutar)** |
| 2a | Personas (N:N), propietarios/arrendatarios/residentes, parqueaderos, tipos y límites de vehículos, regla "vehículo dentro", entradas/salidas, alertas, vista del residente | **Entregada (sin ejecutar)** |
| 2b | Visitantes con invitación/QR y autorización en tiempo real, paquetería, novedades de portería, resumen de seguridad | **Entregada (sin ejecutar)** |
| 3a | Cartera: cargos, facturación masiva, imputación configurable, pagos idempotentes y reversibles, notas crédito/débito, intereses de mora, saldo a favor, estados de cuenta, libro inmutable | **Entregada (sin ejecutar)** |
| 3b | Conciliación bancaria (CSV/XLSX, matching con puntaje, revisión manual, duplicados), Wompi (transacción, webhook idempotente), certificados PDF (paz y salvo, estado de cuenta) | Pendiente |
| 4 | Zonas comunes y reservas (concurrencia), PQRS/convivencia con SLA, notificaciones (email/push/WhatsApp), comunicados, documentos (S3/MinIO) | Pendiente |
| 5 | Mantenimiento, proveedores, asambleas, presupuesto, reportes, cámaras/LPR (adapters), frontend Angular+Ionic+Capacitor+PWA, E2E Playwright, CI/CD | Pendiente |

## Pendientes conocidos de la Fase 1
- Envío real de correo (hoy `LoggingAccountMailAdapter`; el SMTP llega con notificaciones).
- Adaptador S3/MinIO (`StoragePort`): la infraestructura MinIO ya corre, el código llega con documentos.
- Caché de permisos por rol (hoy se consultan por petición; correcto pero mejorable).
- Endpoint para editar datos de la copropiedad (`TENANT_SETTINGS_MANAGE`).
- Logging JSON estructurado (hoy: patrón con `traceId`).

## Pendientes conocidos de la Fase 2b
- **Notificaciones al residente** (push/email/WhatsApp): hoy consulta sus solicitudes (`/my/visitors/requests`) y paquetes (`/my/packages`). El estado `NOTIFIED` de un paquete lo marcará el módulo de notificaciones.
- **Fotos de paquetes y evidencias de novedades**: requieren el almacenamiento S3/MinIO (MinIO ya no se descarga de Docker Hub/Quay; se elegirá otro servidor S3 compatible).
- **Reportes de seguridad** con exportación CSV/XLSX/PDF: llegan con el módulo de reportes; hoy existen los listados filtrables y `/security/summary`.
- Las solicitudes de visitantes sin invitación no expiran automáticamente.
- La duración máxima de una invitación (30 días) es una constante; se hará configurable por copropiedad con la configuración de reglas.

## Pendientes conocidos de la Fase 3a
- **Acuerdos de pago** (cuotas sobre una deuda): el modelo de cargos y notas ya lo permite; falta el módulo propio.
- **Recordatorios de cartera** (próximo vencimiento, mora, pago recibido): llegan con el módulo de notificaciones.
- **Bloqueo de reservas por mora**: la configuración (`blockReservationsWhenOverdue`, `overdueDaysForBlock`) ya existe; la aplicará el módulo de reservas.
- Los intereses se calculan por tasa mensual simple / 30 sobre el saldo de cuotas ordinarias y extraordinarias vencidas (no hay interés sobre intereses). La tasa legal máxima la define la administración; el sistema no la valida contra la Superfinanciera.
- Un solo medio de moneda (COP) y zona horaria America/Bogotá fija para "hoy".
- La facturación masiva no es una única transacción (se hace inmueble por inmueble); es idempotente, así que se puede repetir si se interrumpe.
