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
| 5 | Mantenimiento y órdenes de trabajo, proveedores, presupuesto y ejecución de gastos, reportes (CSV/PDF), cámaras LPR desacopladas | **Completada y verificada** |
| 6 | Frontend Angular 20 + Ionic 8 + Capacitor 8 + PWA: portales de administración, portería, residente y plataforma; branding dinámico; guards por permisos; QR; Wompi; build Docker multi-stage | **Completada y verificada** |
| 7 | Autogestión de datos y canales de la copropiedad; nómina parametrizable; WhatsApp Cloud API entrante/saliente con firma e idempotencia; asistente con proveedores intercambiables; correo SMTP; Playwright, JaCoCo y CI/CD | **Completada y verificada** |

## Pendientes conocidos de la Fase 1
- Adaptador S3/MinIO opcional: la infraestructura existe bajo perfil `storage`; el modo por defecto es almacenamiento
  local persistente.
- Caché de permisos por rol (hoy se consultan por petición; correcto pero mejorable).
- Logging JSON estructurado (hoy: patrón con `traceId`).

## Pendientes conocidos de la Fase 2b
- **Fotos de paquetes y evidencias de novedades**: requieren el almacenamiento S3/MinIO (MinIO ya no se descarga de Docker Hub/Quay; se elegirá otro servidor S3 compatible).
- Las solicitudes de visitantes sin invitación no expiran automáticamente.
- La duración máxima de una invitación (30 días) es una constante; se hará configurable por copropiedad con la configuración de reglas.

## Pendientes conocidos de la Fase 3a
- **Acuerdos de pago** (cuotas sobre una deuda): el modelo de cargos y notas ya lo permite; falta el módulo propio.
- Los intereses se calculan por tasa mensual simple / 30 sobre el saldo de cuotas ordinarias y extraordinarias vencidas (no hay interés sobre intereses). La tasa legal máxima la define la administración; el sistema no la valida contra la Superfinanciera.
- Un solo medio de moneda (COP) y zona horaria America/Bogotá fija para "hoy".
- La facturación masiva no es una única transacción (se hace inmueble por inmueble); es idempotente, así que se puede repetir si se interrumpe.

## Pendientes conocidos de la Fase 3b
- El archivo del extracto vive solo en memoria durante el procesamiento (no se guarda en S3 hasta la fase de documentos). Si el servidor se reinicia a mitad, la importación queda FAILED y se vuelve a cargar.
- El puntaje usa una foto de la deuda al iniciar la importación: dos pagos del mismo inmueble en un mismo archivo no ven el efecto del primero.
- Los pesos de las señales son constantes documentadas (código 0,60 · documento 0,40 · nombre 0,25 · valor 0,25 · pagador conocido 0,30); el umbral, la brecha de ambigüedad y la aplicación automática sí son configurables.
- Formatos: `.csv` y `.xlsx` (primera hoja). Los `.xls` antiguos y los extractos PDF no están soportados.
- La cola de importaciones es interna al proceso (2 hilos, cola de 20); con varias réplicas del backend habría que moverla a Redis.

## Pendientes conocidos de la Fase 6/7
- Notificaciones push nativas: la PWA usa service worker; Capacitor requiere integrar un proveedor push (FCM/APNs).
- Firma y publicación en App Store/Google Play: los proyectos Android/iOS están incluidos, pero requieren credenciales y
  cuentas de desarrollador.
- Compilación nativa: requiere Android SDK/Android Studio y Xcode/CocoaPods; `cap:sync` exige `APP_PUBLIC_URL` HTTPS y
  `APP_NAME` para fijar la API y el nombre visible. La cámara ya declara permisos en ambos proyectos.
- El service worker cachea el shell y los assets; las respuestas de negocio se consultan siempre a la API.
- WhatsApp: falta un número de negocio real, token propio, plantillas aprobadas y webhook público para probar entregas
  reales; el flujo entrante ya está verificado con eventos firmados.
- Wompi: el checkout de sandbox requiere el secreto de integridad correcto del panel (distinto del secreto de eventos).
- El cobro de una tarifa fija de 2–5 COP por pago no es viable por las comisiones de Wompi; debe definirse otro modelo.
- Almacenamiento S3, respaldos/monitoreo de producción y dominio/TLS quedan a cargo del despliegue.
