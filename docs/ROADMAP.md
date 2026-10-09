# Hoja de ruta por fases

Cada fase se entrega como ZIP verificable. Las fases posteriores agregan migraciones nuevas (`V2__…`, `V3__…`) en
`db/migration/global` y `db/migration/tenant`; `TenantMigrationRunner` las aplica a todas las copropiedades existentes.

| Fase | Contenido | Estado |
|---|---|---|
| 1 | Infra Docker, multi-tenancy schema-per-tenant, Flyway, JWT + refresh, RBAC, auditoría, membresías con historial, super admin, inmuebles, Swagger, seed, tests de aislamiento | **Completada y verificada** |
| 2a | Personas (N:N), propietarios/arrendatarios/residentes, parqueaderos, tipos y límites de vehículos, regla "vehículo dentro", entradas/salidas, alertas, vista del residente | **Completada y verificada** |
| 2b | Visitantes con invitación/QR y autorización en tiempo real, paquetería, novedades de portería, resumen de seguridad | **Completada y verificada** |
| 3a | Cartera: cargos, facturación masiva, imputación configurable, pagos idempotentes y reversibles, notas crédito/débito, intereses de mora, saldo a favor, estados de cuenta, libro inmutable | **Completada y verificada** |
| 3b | Conciliación bancaria: CSV/XLSX asíncrono, perfiles por banco, matching con puntaje, revisión manual, duplicados, reversión | **Completada y verificada** |
| 3c | Wompi (transacción, webhook con firma e idempotencia, feature flag ONLINE_PAYMENTS) y certificados PDF (paz y salvo, estado de cuenta, propiedad con OpenPDF) | **Completada y verificada** |
| 4 | Zonas comunes y reservas (concurrencia, mora), PQRS/convivencia con SLA, comunicados, notificaciones multicanal, almacenamiento y documentos (StoragePort), asambleas y votación ponderada Ley 675 | **Completada y verificada** |
| 5 | Mantenimiento y órdenes de trabajo, proveedores, presupuesto y ejecución de gastos, reportes (CSV/PDF), cámaras LPR desacopladas, frontend Angular+Ionic+Capacitor+PWA | **Backend completado y verificado (smoke tests 1-20)** |

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

## Pendientes conocidos de la Fase 3b
- El archivo del extracto vive solo en memoria durante el procesamiento (no se guarda en S3 hasta la fase de documentos). Si el servidor se reinicia a mitad, la importación queda FAILED y se vuelve a cargar.
- El puntaje usa una foto de la deuda al iniciar la importación: dos pagos del mismo inmueble en un mismo archivo no ven el efecto del primero.
- Los pesos de las señales son constantes documentadas (código 0,60 · documento 0,40 · nombre 0,25 · valor 0,25 · pagador conocido 0,30); el umbral, la brecha de ambigüedad y la aplicación automática sí son configurables.
- Formatos: `.csv` y `.xlsx` (primera hoja). Los `.xls` antiguos y los extractos PDF no están soportados.
- La cola de importaciones es interna al proceso (2 hilos, cola de 20); con varias réplicas del backend habría que moverla a Redis.
