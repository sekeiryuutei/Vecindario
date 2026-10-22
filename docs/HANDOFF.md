# VECINDAD — Documento Maestro de Traspaso (Handoff)
**Estado actual: fases 1–6, autogestión, nómina, WhatsApp, módulos comunitarios y calidad automatizada**

Plataforma SaaS multi-tenant empresarial de administración integral de copropiedades residenciales en Colombia.
- **Marca:** `APP_NAME` (por defecto: **Vecindad**)
- **Empresa proveedora:** `APP_COMPANY` (por defecto: **CodeVam**)
- Ambos nombres se configuran vía variables de entorno (`.env`); la UI y el manifest PWA los consumen desde la API. Los valores por defecto de desarrollo no se usan en producción: el validador exige configurarlos.
- El pie de página muestra `APP_COMPANY`; no hay una marca de proveedor hardcodeada en la UI.
- **Prohibición absoluta:** Cero placeholders, cero stubs incompletos ("coming soon", TODOs) y cero marcas de proveedores tecnológicos en la UI. La asistencia conversacional es una capacidad interna del canal de WhatsApp, no un módulo visible ni una marca del producto.

---

## 1. Stack Tecnológico y Arquitectura

### 1.1 Backend
- **Lenguaje y Framework:** Java 21 LTS, Spring Boot 3.3.5.
- **Arquitectura:** Hexagonal / Puertos y Adaptadores por módulo (`domain`, `application`, `port/in`, `port/out`, `adapter/in/web`, `adapter/out/persistence`).
- **Persistencia y Aislamiento:** PostgreSQL 16 con estrategia **Schema-per-Tenant** (un esquema independiente por copropiedad: `tenant_demo_norte`, `tenant_demo_sur`, etc. y esquema global `public`).
- **Migraciones de BD:** Flyway automatizado:
  - Migraciones globales: `src/main/resources/db/migration/global` (hasta `V17__community_permissions.sql`).
  - Migraciones por tenant: `src/main/resources/db/migration/tenant` (hasta `V16__unit_files.sql`).
  - `TenantMigrationRunner`: Aplica migraciones dinámicamente a todos los esquemas de copropiedad al arrancar o al aprovisionar un nuevo tenant.
- **Seguridad y Multi-tenancy:**
  - Stateless JWT con firma HMAC-SHA256, rotación de refresh tokens en base de datos.
  - Validación estricta del tenant: el `tenantId` se extrae exclusivamente del JWT (`tid`) validando membresía activa en BD en cada petición (`JwtAuthenticationFilter`). Jamás se confía en parámetros enviados por el cliente.
  - Contexto de tenant en hilo (`TenantContext`) y abstracción `TenantJdbc` con comodín `{s}` para aislar consultas SQL al esquema del tenant activo.
- **Transaccionalidad Financiera:** Bloqueo pesimista por unidad (`SELECT ... FOR UPDATE`), idempotencia vía cabecera `Idempotency-Key`, libro mayor contable inmutable mediante triggers PostgreSQL. Dinero siempre en `BigDecimal`/`NUMERIC(14,2)`.
- **Integraciones:** Pasarela Wompi (firma de integridad, webhook SHA-256 e idempotencia), correo SMTP con nombre visible configurable, WhatsApp Cloud API (webhook firmado, idempotencia por `message_id`, respuestas salientes), asistente conversacional con proveedores intercambiables y respuesta local de respaldo, PDF con OpenPDF, `StoragePort` y `CameraProvider` desacoplados.

### 1.2 Infraestructura Docker
- `docker-compose.yml` orquestado con red interna `vecindad_net`:
  - `vecindad_backend`: Spring Boot 3.3.5 (puerto 48121 -> 8080).
  - `vecindad_postgresql`: PostgreSQL 16 Alpine (puerto 48122 -> 5432).
  - `vecindad_redis`: Redis 7 Alpine (puerto 48123 -> 6379).
  - `vecindad_nginx`: Nginx 1.27 Alpine (puerto 48124 -> 80) actuando como reverse proxy hacia el backend y frontend.
  - `vecindad_frontend`: build multi-stage de Angular/Ionic y Nginx sirviendo `dist/vecindad/browser` con cabeceras de
    seguridad, service worker y fallback de rutas.
  - Volumen `vecindad_storage` para documentos/certificados y healthchecks en todos los servicios.

---

## 2. Resumen Completo de Fases Implementadas

| Fase | Alcance y Módulos | Estado | Verificación |
|---|---|---|---|
| **Fase 1** | Infraestructura Docker, PostgreSQL multi-esquema, Redis, JWT + Refresh tokens rotativos, RBAC global, Auditoría inmutable, Membresías con historial, Creación de empresas y copropiedades, Unidades inmobiliarias (`properties`), Swagger OpenAPI, Seeds demo. | **Completada** | Tests unitarios + Smoke tests |
| **Fase 2a** | Personas (modelo N:N persona-inmueble), Residentes, Propietarios, Arrendatarios, Parqueaderos, Vehículos y límites por unidad, Regla atómica "vehículo dentro no se modifica", Control de acceso vehicular (entradas/salidas en una sola CTE SQL), Alertas de seguridad, Portal residente. | **Completada** | Tests unitarios + Smoke tests |
| **Fase 2b** | Visitantes con invitaciones QR de un solo uso (SHA-256), Flujo de autorización en tiempo real portero-residente (Walk-in), Control de acceso peatonal, Paquetería con tracking y entregas, Bitácora de novedades/incidentes de portería, Panel de resumen de seguridad. | **Completada** | Tests unitarios + Smoke tests |
| **Fase 3a** | Cartera y finanzas: Configuración financiera, Facturación masiva de expensas con cálculo de mora simple diario (Ley 675), Pagos idempotentes y reversibles, Imputación configurable, Saldo a favor, Notas crédito/débito, Estados de cuenta, Libro mayor inmutable. | **Completada** | Tests unitarios + Smoke tests |
| **Fase 3b** | Conciliación bancaria: Importación asíncrona de extractos CSV/XLSX, Perfiles por entidad bancaria, Motor de matching heurístico con puntajes y umbrales, Identidad estable por hash (`tx_hash`), Revisión manual, Reversión de conciliación. | **Completada** | Tests unitarios + Smoke tests |
| **Fase 3c** | Pasarela de pagos Wompi: Intenciones de pago, Webhook con validación criptográfica de firma SHA-256 e idempotencia, Feature flag `ONLINE_PAYMENTS`. Certificados digitales PDF (Paz y Salvo, Estado de Cuenta, Propiedad) con código QR y verificación pública hash. | **Completada** | Tests unitarios + Smoke tests |
| **Fase 4** | Zonas comunes y reservas con validación de horarios y bloqueo automático por mora, PQRS y convivencia con SLA estricto (24h para ruido / Código de Policía Ley 1801) y notas privadas, Comunicados segmentados con acuse de lectura, Notificaciones multicanal (Email, Push, WhatsApp), Repositorio documental con permisos por rol y `StoragePort`, Asambleas generales ordinarias/extraordinarias con quórum dinámico y votación ponderada por coeficientes (Ley 675). | **Completada** | Tests unitarios + Smoke tests |
| **Fase 5** | Catálogo de proveedores y contratistas con seguimiento de contratos y pólizas de seguro (`suppliers`), Mantenimiento preventivo y correctivo con activos, planes y órdenes de trabajo (`maintenance`), Presupuesto anual con rubros de ingresos/gastos y balance de ejecución en tiempo real (`budget`), Reportes consolidados de cartera y exportación en CSV y PDF (`reports`), Adaptador desacoplado de cámaras LPR (`camera`). | **Completada** | **Incluida en la suite actual (205 unitarias + 51 IT)** |
| **Fase 6** | **Frontend Web, PWA y Móvil:** Angular 20 standalone, Ionic 8, Capacitor 8 (Android/iOS), PWA con service worker. Portales de administración, portería, residente y superadmin; páginas públicas de login, recuperación, selección de copropiedad, verificación de certificados y resultado Wompi; dashboards y navegación filtrados por permisos reales. | **Completada** | **`npm run build`, Playwright 17/17 y Docker build multi-stage OK** |
| **Autogestión y nómina** | Edición de datos de la copropiedad y activación de canales autogestionables (`WHATSAPP`, `CAMERAS`, `LPR`, `ASSEMBLIES`); pagos en línea sigue siendo una decisión de plataforma. Nómina por copropiedad con parámetros, empleados, periodos, cálculo de aportes/provisiones, pago, anulación y exportación CSV. | **Completada** | **Pruebas unitarias, smoke y flujo API/UI verificados** |
| **WhatsApp y calidad** | Webhook entrante de WhatsApp Cloud API con firma e idempotencia, respuestas salientes, asistente con adaptadores intercambiables y respaldo local; correo SMTP real; Playwright para los cuatro portales; JaCoCo con umbral de 45% de líneas y CI/CD en GitHub Actions. | **Completada** | **Webhook GET/POST/duplicado/firma inválida OK; E2E 17/17; cobertura 52,49% de líneas** |
| **Reservas, pagos, integraciones y mascotas** | Términos de uso y ventanas de aprobación por zona común, reservas con pago previo (`PENDING_PAYMENT`) facturadas con Wompi, panel general multi-copropiedad (`/my/tenant-overview`), credenciales de Wompi/IA cifradas (AES-GCM) administradas por copropiedad desde el área de plataforma y registro de mascotas por inmueble con vacunación y microchip único. | **Completada** | **205 unitarias + 51 IT, smoke de 30 secciones OK, E2E 17/17** |
| **Comunidad y operación** | Junta directiva con cargos e historial, citas de residentes con agenda y reglas de horario, capacitaciones con cupos, inscripciones y asistencia, directorio de personal operativo y expediente del inmueble con notas públicas/internas y documentos asociados. Matriz de roles con restablecimiento por permiso o por rol y exportación CSV. | **Completada** | **205 unitarias + 51 IT, smoke de 30 secciones OK, E2E 17/17 y revisión responsive** |

---

## 3. Catálogo de Endpoints de la API Backend

Todos los endpoints responden en `http://localhost:48124/api/v1` (o a través de Nginx en el puerto configurado). Requieren `Content-Type: application/json` y autenticación Bearer Token excepto los públicos.

### Autenticación y Plataforma
- `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout`
- `POST /auth/select-tenant`: Selecciona la copropiedad activa y emite token específico para el tenant.
- `GET /auth/me`, `POST /auth/change-password`, `POST /auth/forgot-password`, `POST /auth/reset-password`
- `GET /public/config`: Parámetros públicos de la plataforma (Branding: `appName`, `company`).
- `GET /public/manifest.webmanifest`: Manifest PWA generado con `APP_NAME` y los iconos del frontend.
- `GET /tenants/current`: Datos de la copropiedad activa.
- `PUT /tenants/current`: Edición de datos básicos por el administrador de la copropiedad.
- `PUT /tenants/current/features/{feature}`: Activación de `WHATSAPP`, `CAMERAS`, `LPR` y `ASSEMBLIES`; `ONLINE_PAYMENTS` se administra desde plataforma.
- `GET /audit`: Registro inmutable de auditoría del tenant.
- `GET /roles`, `GET /roles/permissions`, `PUT /roles/{rol}/permissions/{perm}`: matriz de permisos efectivos.
- `DELETE /roles/{rol}/permissions/{perm}` y `DELETE /roles/{rol}/permissions`: restablecen un permiso o el rol completo
  a los valores base de la plataforma; `ADMINISTRADOR` no se puede restablecer.
- `GET/POST /platform/companies`, `GET/POST /platform/tenants`: Gestión de nivel Super Admin.
- `GET /my/tenant-overview`: Resumen de todas las copropiedades asignadas (rol, permisos y métricas) para el panel general.
- `GET/PUT/DELETE /platform/tenants/{tenantId}/integrations[/wompi|/ai]`: Credenciales de Wompi y del proveedor de IA por
  copropiedad, cifradas con AES-GCM; la API solo devuelve el estado enmascarado.

### Inmuebles, Personas y Vehículos (Fases 1 y 2a)
- `GET/POST /properties`, `GET/PUT/DELETE /properties/{id}`
- `GET/POST /residents`, `GET/PUT/DELETE /residents/{id}`, `GET /my/units`
- `GET/POST /vehicles`, `GET/PUT/DELETE /vehicles/{id}`, `GET/POST /my/vehicles`
- `GET/POST /parking-spaces`, `GET/PUT /vehicle-types/{code}`, `GET/PUT /vehicle-limits/{type}`
- `GET /access/vehicles/lookup?plate=`, `POST /access/entry`, `POST /access/exit`, `GET /access/vehicles/inside`
- `GET /access/alerts`, `POST /access/alerts/{id}/resolve`

### Portería, Visitantes y Paquetería (Fase 2b)
- `POST /my/visitors/invitations`: Crea invitación y emite token QR de un solo uso.
- `POST /visitors/validate-qr`, `POST /visitors/check-in-qr`: Validación y registro de ingreso por QR en portería.
- `POST /visitors/walk-in`: Solicitud de visitante sin invitación en portería.
- `GET /my/visitors/requests`: Residente consulta solicitudes pendientes de autorización.
- `POST /my/visitors/requests/{id}/authorize`, `POST /my/visitors/requests/{id}/reject`
- `POST /visitors/visits/{id}/check-in`, `POST /visitors/visits/{id}/check-out`
- `GET/POST /packages`, `POST /packages/{id}/deliver`, `GET /my/packages`
- `GET/POST /incidents`, `PATCH /incidents/{id}/status`, `GET /security/summary`

### Cartera, Facturación y Conciliación (Fases 3a y 3b)
- `GET/PUT /billing/settings`, `POST /billing/runs/ordinary`, `POST /billing/runs/interest`
- `GET /billing/portfolio`: Listado general de saldos y morosidad.
- `POST /payments`: Registro de pagos con `Idempotency-Key`.
- `POST /payments/{id}/reverse`: Reversión justificada de pagos.
- `GET /my/statement?unitId=`: Estado de cuenta consolidado del residente.
- `GET /billing/units/{unitId}/ledger`: Libro mayor de movimientos contables inmutable.
- `POST /bank-reconciliation/imports`: Carga asíncrona de extractos bancarios.
- `GET /bank-reconciliation/imports/{id}`, `GET /bank-reconciliation/rows`
- `POST /bank-reconciliation/rows/{id}/match`, `/reject`, `/revert`: Conciliación y revisión de filas de extracto.

### Wompi y Certificados PDF (Fase 3c)
- `POST /my/payments/online`: Creación de intención de pago en línea (mínimo `$1.500 COP`).
- `GET /my/payments/online/{reference}`: Consulta del estado de una intención.
- `POST /public/wompi/events`: Webhook seguro con verificación de hash SHA-256 e idempotencia.
- `GET /certificates/units/{unitId}/paz-y-salvo`, `.../statement`, `.../ownership`, `.../history`: Certificados PDF administrativos.
- `GET /my/certificates/paz-y-salvo`, `.../statement`, `.../ownership`, `.../history`: Certificados del residente.
- `GET /public/certificates/verify/{code}`: Validación pública del certificado mediante código y hash de integridad.

### Zonas Comunes, Convivencia y Asambleas (Fase 4)
- `GET/POST /common-areas`, `GET /common-areas/{id}/availability?date=`
- `POST/GET /my/reservations`, `POST /my/reservations/{id}/cancel`
- `GET /reservations`, `POST /reservations/{id}/approve`, `POST /reservations/{id}/reject`
- `POST/GET /my/pqrs`, `GET /pqrs`, `POST /pqrs/{id}/messages` (soporta `isInternal`), `GET /pqrs/metrics/sla`
- `POST/GET /communications`, `PATCH /communications/{id}/publish`, `GET /my/communications`
- `GET/PUT /notifications/settings`, `POST /notifications/portfolio-reminders`, `GET /notifications/logs`
- `POST /documents` (con `unitId` opcional), `GET /documents?category=&unitId=`, `GET /documents/{id}/download`,
  `GET /my/documents`, `GET /my/documents/{id}/download`
- `POST/GET /assemblies`, `POST /assemblies/{id}/check-in`, `GET /assemblies/{id}/quorum`
- `POST/GET /assemblies/{id}/topics`, `POST /assemblies/topics/{id}/open-vote`, `POST /assemblies/topics/{id}/close-vote`
- `POST /my/assemblies/topics/{id}/vote` (ponderado por coeficiente de copropiedad según Ley 675).

### Nómina, WhatsApp y Asistencia Conversacional
- `GET/PUT /payroll/settings`: Parámetros laborales y porcentajes por copropiedad.
- `GET/POST /payroll/employees`, `GET/PUT/DELETE /payroll/employees/{id}`: Empleados y vinculación laboral.
- `GET/POST /payroll/runs`, `GET /payroll/runs/{id}`, `POST /payroll/runs/{id}/recalculate`, `POST /payroll/runs/{id}/pay`, `POST /payroll/runs/{id}/void`.
- `GET /payroll/runs/{id}/export.csv`: Exportación de la corrida en CSV (`Accept: text/csv`).
- `GET /public/whatsapp/webhook`: Verificación del webhook de Meta Cloud API.
- `POST /public/whatsapp/webhook`: Mensajes entrantes con validación `X-Hub-Signature-256` e idempotencia. Las notificaciones salientes usan el mismo canal desde el módulo de notificaciones.

### Proveedores, Mantenimiento, Presupuesto y Reportes (Fase 5)
- `GET/POST /suppliers`: Directorio de proveedores.
- `GET/PUT/DELETE /suppliers/{id}`, `GET/POST /suppliers/{id}/contracts`
- `GET/POST /maintenance/assets`: Activos críticos y maquinaria de la copropiedad.
- `GET/POST /maintenance/plans`: Planes de mantenimiento preventivo.
- `GET/POST /maintenance/work-orders`: Órdenes de trabajo preventivas/correctivas.
- `PATCH/PUT /maintenance/work-orders/{id}/status`: Cierre de órdenes con costo real y notas de liquidación.
- `GET/POST /budgets`: Presupuestos anuales por vigencia.
- `GET/POST /budgets/{id}/items`: Rubros de ingresos y egresos.
- `POST/GET /budgets/items/{itemId}/executions`: Registro y consulta de ejecuciones presupuestales.
- `GET /budgets/{year}/summary`: Balance financiero de ejecución vs. presupuestado.
- `GET /reports/portfolio`: Informe consolidado de cartera y morosidad.
- `GET /reports/portfolio/export/csv`: Exportación de cartera a archivo CSV estructurado.
- `GET /reports/portfolio/export/pdf`: Exportación de cartera a documento PDF oficial con OpenPDF.
- `GET /reports/budget/{year}`: Informe de ejecución presupuestal.
- `GET /reports/maintenance`: Informe de métricas y costos de mantenimiento.
- `GET /cameras/status`: Estado de operatividad de cámaras LPR.
- `POST /cameras/detect`: Detección y verificación de placas vehiculares autorizadas vs. visitantes.

### Comunidad y operación
- `GET /board?status=`, `POST /board`, `PUT/DELETE /board/{id}`: junta directiva; una persona solo puede tener un cargo
  activo y los cargos distintos de `VOCAL` son únicos.
- `GET /appointments?status=&from=&to=&q=`, `PUT/DELETE /appointments/{id}`: agenda administrativa y cierre de citas.
- `GET /my/appointments`, `POST /my/appointments`, `POST /my/appointments/{id}/cancel`: citas del residente. Horario de
  lunes a viernes 08:00–17:00 (Bogotá), mínimo 2 horas de anticipación, máximo 60 días y franja única activa.
- `GET /trainings?status=&from=`, `POST /trainings`, `PUT/DELETE /trainings/{id}`, `POST /trainings/{id}/cancel`:
  capacitaciones con cupos y estados `SCHEDULED`, `CANCELLED` y `COMPLETED`.
- `GET /trainings/{id}/enrollments`, `PUT /trainings/enrollments/{id}`: inscripciones y asistencia.
- `GET /my/trainings`, `POST/DELETE /my/trainings/{id}/enroll`: inscripción y cancelación propia.
- `GET /staff?q=&status=`, `POST /staff`, `PUT/DELETE /staff/{id}`: directorio de personal operativo, independiente de la
  nómina.
- `GET /properties/{unitId}/file`, `POST /properties/{unitId}/notes`,
  `PUT/DELETE /properties/{unitId}/notes/{id}`: expediente del inmueble con relaciones, vehículos, mascotas, documentos y
  notas internas o públicas.
- `GET /my/units/{unitId}/file`: expediente propio del residente, limitado a notas públicas y documentos visibles por rol.

---

## 4. Cómo Correr y Verificar el Sistema

### 4.1 Comandos de Despliegue (PowerShell)
```powershell
# 1. Asegurar archivo .env
cp .env.example .env

# 2. Levantar todos los contenedores (Postgres, Redis, backend, frontend y Nginx)
docker compose up -d --build

# 3. Ver logs del backend para confirmar arranque limpio y migraciones Flyway
docker logs vecindad_backend --tail 50

# 4. Ejecutar pruebas unitarias, integración y cobertura
cd backend
mvn -B verify
# Resultado esperado: 205 unitarias + 51 IT, 0 fallos; JaCoCo >= 45% de líneas (52,49% actual)
cd ..

# 5. Compilar el frontend y ejecutar Playwright
cd frontend
npm ci
npx playwright install chromium
npm run build
$env:E2E_BASE_URL='http://localhost:48124'
$env:E2E_PASSWORD='Demo#2026!'
npm run test:e2e
cd ..

# 6. Smoke test integral (30 secciones)
powershell -ExecutionPolicy Bypass -File .\scripts\smoke-test.ps1
# Resultado esperado: build sin errores, Playwright 17/17 y mensaje final "TODO OK"
```

### 4.2 Credenciales de Demostración
- **Contraseña universal de demo:** `Demo#2026!` (configurable vía `APP_SEED_PASSWORD`).
- **Super Administrador de Plataforma:** `superadmin@vecindad.local`
- **Administrador Multi-copropiedad:** `admin@vecindad.local` (acceso a `demo_norte` y `demo_sur`).
- **Administrador Demo Sur:** `admin.sur@demo-sur.local`
- **Secretaría Norte:** `secretaria@demo-norte.local`
- **Portería Norte:** `portero@demo-norte.local`
- **Contador Norte:** `contador@demo-norte.local`
- **Consejo Norte:** `consejo@demo-norte.local`
- **Propietario / Residente Norte:** `propietario@demo-norte.local` (asociado a apartamentos `T1-101`, `T1-102` y vehículo `ABC123`).

---

## 5. Decisiones Críticas de Arquitectura (Reglas de Oro)

1. **Multi-tenancy por Esquema:**
   - La base de datos tiene `public` (tablas globales de usuarios, empresas administradoras, copropiedades/tenants, roles, auditoría y tokens) y un esquema por cada copropiedad (ej. `tenant_demo_norte`, `tenant_demo_sur`).
   - Los datos operativos de la copropiedad **NUNCA** se mezclan en el esquema público ni entre esquemas de diferentes copropiedades.
   - Toda consulta a tablas operativas usa `TenantJdbc` con el marcador `{s}`. `TenantContext.getSchema()` inyecta el esquema validado.

2. **Seguridad y Autorización:**
   - En peticiones autenticadas, el `tenant_id` se extrae del JWT verificado. Si el usuario administra múltiples copropiedades, debe invocar `POST /auth/select-tenant` para emitir un JWT ligado a la copropiedad activa.
   - Las autorizaciones se evalúan mediante permisos específicos asignados a roles (`role_permissions`). El `ADMINISTRADOR` tiene privilegios completos sobre la copropiedad seleccionada.

3. **Inmutabilidad y Concurrencia:**
   - Los pagos nunca se eliminan ni editan; solo se pueden revertir mediante `POST /payments/{id}/reverse` dejando registro contable compensatorio.
   - Las operaciones de entrada y salida de portería, así como el check-in de visitantes QR, se realizan en sentencias SQL atómicas individuales (CTE `UPDATE ... RETURNING` + `INSERT`) para evitar condiciones de carrera.

4. **Reglas de Entrega y Git:**
   - El código debe mantenerse en inglés para la arquitectura/dominio (clases, variables, métodos) y en español para los textos y mensajes orientados al usuario (UI, logs amigables, mensajes de error).
   - Todos los commits de Git deben ser registrados con el autor del usuario: `Victor Burbano <vburbano@live.com>`.

5. **Autogestión acotada:** el administrador de la copropiedad puede editar datos básicos y activar/desactivar únicamente `WHATSAPP`, `CAMERAS`, `LPR` y `ASSEMBLIES`. `ONLINE_PAYMENTS` sigue siendo una decisión de la plataforma para evitar que una copropiedad habilite cobros sin contrato y llaves de producción.

6. **Nómina parametrizable:** los porcentajes, bases y provisiones son configurables por copropiedad. El sistema calcula y documenta, pero no certifica cumplimiento laboral ni reemplaza la revisión de un contador; la administración debe validar los parámetros y la normativa vigente.

7. **WhatsApp y asistencia:** el webhook de Meta Cloud API valida `X-Hub-Signature-256`, es idempotente por `message_id` y solo responde si el canal está habilitado. Las respuestas automáticas usan adaptadores intercambiables con respaldo local; la UI no muestra proveedores ni marcas de asistencia. Los saldos solo se incluyen si `WHATSAPP_AI_INCLUDE_FINANCIAL_DATA=true`.

8. **Wompi:** el monto mínimo en línea es `$1.500 COP` (mínimo de la pasarela); el redirect debe ser HTTPS público y los secretos de integridad y de eventos deben ser distintos. El webhook se firma y se procesa con idempotencia.

9. **Calidad:** Playwright cubre 17 flujos críticos de administración, residente, portería y plataforma, incluida la
   verificación responsive de los módulos comunitarios. JaCoCo se ejecuta en `verify` con umbral mínimo de 45% de líneas
   (52,49% actual). El smoke test cubre 30 secciones, incluidas autogestión, nómina, WhatsApp, mascotas, junta directiva,
   citas, capacitaciones, personal operativo, expediente del inmueble y matriz de roles.

---

## 6. Fase 6 implementada (Frontend Web, PWA y Móvil)

- **Ubicación:** `./frontend`, Angular 20.3 standalone + Ionic 8.8 + Capacitor 8.5 + PWA.
- **Salida de build:** `dist/vecindad/browser`, servida por el Nginx del contenedor `vecindad_frontend` y proxied por el
  Nginx del borde. El servicio no usa perfiles: `docker compose up -d --build` levanta frontend, backend e
  infraestructura en un solo comando.
- **Branding:** `BrandingService` inicializa `APP_NAME`/`APP_COMPANY` desde `GET /api/v1/public/config`; el manifest PWA
  se sirve desde `GET /api/v1/public/manifest.webmanifest` con `APP_NAME`, y la UI nunca hardcodea la marca.
- **Sesión y permisos:** tokens en almacenamiento local, refresh automático en 401, interceptores de error, guards de
  autenticación/permisos y menús filtrados por los permisos reales de la copropiedad activa.
- **Rutas principales:**
  - Públicas: `/login`, `/recuperar`, `/restablecer`, `/seleccionar-copropiedad`, `/verificar-certificado/:code`,
    `/pagos/wompi/resultado`.
  - Administración: dashboard, inmuebles, personas, vehículos, cartera, pagos, conciliación, certificados, reservas,
    PQRS, comunicados, notificaciones, documentos, asambleas, proveedores, mantenimiento, presupuesto, reportes,
    nómina, junta directiva, citas, capacitaciones, personal operativo, expedientes, auditoría, usuarios, roles y
    configuración. La barra superior incluye “Cambiar copropiedad” cuando el usuario tiene más de un tenant.
  - Portería: control de acceso, visitantes con escáner QR, paquetería, novedades y consulta del personal operativo.
  - Residente: inmuebles, vehículos, estado de cuenta, pagos Wompi, certificados, visitantes, reservas, PQRS,
    comunicados, asambleas, documentos, paquetes, junta directiva, citas, capacitaciones y expediente del inmueble.
  - Plataforma: empresas, copropiedades, auditoría global y dashboard de superadmin.
- **PWA y móvil:** manifest dinámico con `APP_NAME`, iconos 192/512/maskable, service worker con precarga de assets y
  navegación SPA; proyectos Android e iOS generados con Capacitor, permisos de cámara declarados, `runtime-config.json`
  para fijar la API absoluta de la app nativa y script de sincronización que toma `APP_NAME`/`APP_PUBLIC_URL`
  (`npm run cap:sync`, `cap:android`, `cap:ios`).
- **Verificación:** `npm run build` finaliza sin errores ni warnings de plantillas; Playwright supera 17/17; `npm run cap:sync`
  sincroniza Android/iOS con la API absoluta y el nombre de la app; el backend pasa 205 unitarias y 51 IT; la imagen
  Docker multi-stage del frontend se construye a partir de `docker/frontend/Dockerfile`.
- **Operación:** el Nginx del frontend sirve `index.html`, `ngsw.json` y `ngsw-worker.js` sin caché, aplica CSP y
  cabeceras de seguridad, y usa fallback a `index.html` para rutas profundas.

---

## 7. Estado de entrega y pendientes externos

**Verificado en el entorno local:** arranque Docker, migraciones globales hasta V17 y por tenant hasta V16, 205 unitarias,
51 IT, cobertura 52,49% de líneas, Playwright 17/17, smoke de 30 secciones, autogestión por API, nómina
calculada/anulada/exportada, correo SMTP recibido, webhook de WhatsApp firmado/idempotente, pago Wompi aprobado en sandbox
con webhook reconciliado, reservas con términos, aprobación nocturna y pago previo (`PENDING_PAYMENT` → webhook →
`PENDING_APPROVAL`), panel general multi-copropiedad, integraciones por copropiedad cifradas, desglose de comisión y tarifa
por pago en línea, junta directiva, citas, capacitaciones, personal operativo, expediente del inmueble con documentos
aislados por inmueble, matriz de roles con restablecimiento y configuración productiva fail-closed.

**Pendiente para producción (requiere decisiones o credenciales del propietario):**

- Dominio público, DNS y TLS; `APP_PUBLIC_URL`, `WOMPI_REDIRECT_URL` y `CORS_ALLOWED_ORIGINS` definitivos.
- Secretos productivos: JWT, PostgreSQL, Redis, SMTP, Wompi y WhatsApp. El `WOMPI_INTEGRITY_SECRET` debe ser el secreto
  de integridad del panel, **distinto** del secreto de eventos. En sandbox ya se completó un pago real con tarjeta de
  prueba y el webhook reconcilió la intención; en producción faltan las llaves y secretos productivos.
- `INTEGRATIONS_ENCRYPTION_KEY` (32+ bytes) para cifrar las credenciales de Wompi y de IA que defina cada copropiedad; sin
  esta variable el backend no arranca en producción.
- Wompi de producción con llaves y secretos productivos; el checkout de desarrollo debe dejar `WOMPI_REDIRECT_URL` vacío
  porque Wompi rechaza redirects HTTP/localhost. En producción el redirect y el webhook deben ser HTTPS públicos.
- WhatsApp: número de negocio real, token rotado, app de Meta dedicada, plantillas aprobadas y webhook público. El envío
  y la recepción real ya se validaron con el token actual; ese token se usará temporalmente en otra aplicación, por lo
  que el canal quedó deshabilitado en el `.env` local. No debe reutilizarse en producción sin una app dedicada.
- Almacenamiento S3 compatible (aún solo existe `LocalStorageAdapter`), firma/publicación Android/iOS y notificaciones
  push FCM/APNs. El cobro por pago en línea ya está implementado (deuda + comisión de la pasarela + tarifa de la
  plataforma, por defecto $2.000); falta definir en producción cómo se liquida la tarifa al operador de la plataforma
  (Wompi con tarifas avanzadas/split o dispersión a terceros desde la cuenta de la plataforma).
- Respaldos programados, restauración probada y monitoreo/alertas del host y los contenedores.
