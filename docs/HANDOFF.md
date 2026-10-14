# VECINDAD — Documento Maestro de Traspaso (Handoff)
**Estado al cierre de la Fase 5 (Backend 100% Completado y Verificado)**

Plataforma SaaS multi-tenant empresarial de administración integral de copropiedades residenciales en Colombia.
- **Marca:** `APP_NAME` (por defecto: **Vecindad**)
- **Empresa proveedora:** `APP_COMPANY` (por defecto: **CodeVam**)
- Ambos nombres son dinámicos y se configuran vía variables de entorno (`.env`), nunca hardcodeados en el código ni en la UI.
- Powered by CodeVam (discreto en footer).
- **Prohibición absoluta:** Cero referencias a IA, cero placeholders, cero stubs incompletos ("coming soon", TODOs). Todo debe ser código de producción real.

---

## 1. Stack Tecnológico y Arquitectura

### 1.1 Backend
- **Lenguaje y Framework:** Java 21 LTS, Spring Boot 3.3.5.
- **Arquitectura:** Hexagonal / Puertos y Adaptadores por módulo (`domain`, `application`, `port/in`, `port/out`, `adapter/in/web`, `adapter/out/persistence`).
- **Persistencia y Aislamiento:** PostgreSQL 16 con estrategia **Schema-per-Tenant** (un esquema independiente por copropiedad: `tenant_demo_norte`, `tenant_demo_sur`, etc. y esquema global `public`).
- **Migraciones de BD:** Flyway automatizado:
  - Migraciones globales: `src/main/resources/db/migration/global` (hasta `V9__phase5_global.sql`).
  - Migraciones por tenant: `src/main/resources/db/migration/tenant` (hasta `V8__phase5_tenant.sql`).
  - `TenantMigrationRunner`: Aplica migraciones dinámicamente a todos los esquemas de copropiedad al arrancar o al aprovisionar un nuevo tenant.
- **Seguridad y Multi-tenancy:**
  - Stateless JWT con firma HMAC-SHA256, rotación de refresh tokens en base de datos.
  - Validación estricta del tenant: el `tenantId` se extrae exclusivamente del JWT (`tid`) validando membresía activa en BD en cada petición (`JwtAuthenticationFilter`). Jamás se confía en parámetros enviados por el cliente.
  - Contexto de tenant en hilo (`TenantContext`) y abstracción `TenantJdbc` con comodín `{s}` para aislar consultas SQL al esquema del tenant activo.
- **Transaccionalidad Financiera:** Bloqueo pesimista por unidad (`SELECT ... FOR UPDATE`), idempotencia vía cabecera `Idempotency-Key`, libro mayor contable inmutable mediante triggers PostgreSQL. Dinero siempre en `BigDecimal`/`NUMERIC(14,2)`.
- **Integraciones:** Pasarela de pagos Wompi (firmas SHA-256 e idempotencia de webhooks), generación de PDF nativa con OpenPDF, adaptador de almacenamiento desacoplado (`StoragePort`), adaptador desacoplado de cámaras LPR (`CameraProvider`).

### 1.2 Infraestructura Docker
- `docker-compose.yml` orquestado con red interna `vecindad_net`:
  - `vecindad_backend`: Spring Boot 3.3.5 (puerto 48121 -> 8080).
  - `vecindad_postgresql`: PostgreSQL 16 Alpine (puerto 48122 -> 5432).
  - `vecindad_redis`: Redis 7 Alpine (puerto 48123 -> 6379).
  - `vecindad_nginx`: Nginx 1.27 Alpine (puerto 48124 -> 80) actuando como reverse proxy hacia el backend y frontend.
  - `vecindad_frontend` (preparado bajo perfil `frontend`): Nginx sirviendo la build de Angular/Ionic (`dist/vecindad/browser`).

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
| **Fase 5** | Catálogo de proveedores y contratistas con seguimiento de contratos y pólizas de seguro (`suppliers`), Mantenimiento preventivo y correctivo con activos, planes y órdenes de trabajo (`maintenance`), Presupuesto anual con rubros de ingresos/gastos y balance de ejecución en tiempo real (`budget`), Reportes consolidados de cartera y exportación en CSV y PDF (`reports`), Adaptador desacoplado de cámaras LPR (`camera`). | **Completada** | **83/83 unit tests OK, 20/20 secciones smoke test OK** |
| **Fase 6** | **Frontend Móvil y Web:** Angular 18+ con Ionic 8, Capacitor (iOS/Android), PWA, diseño responsive multi-rol (Administrador, Portero, Contador, Consejo, Residente/Propietario). | **PENDIENTE (Siguiente Paso)** | A construir |

---

## 3. Catálogo de Endpoints de la API Backend

Todos los endpoints responden en `http://localhost:48124/api/v1` (o a través de Nginx en el puerto configurado). Requieren `Content-Type: application/json` y autenticación Bearer Token excepto los públicos.

### Autenticación y Plataforma
- `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout`
- `POST /auth/select-tenant`: Selecciona la copropiedad activa y emite token específico para el tenant.
- `GET /auth/me`, `POST /auth/change-password`, `POST /auth/forgot-password`, `POST /auth/reset-password`
- `GET /public/config`: Parámetros públicos de la plataforma (Branding: `appName`, `appCompany`).
- `GET /tenants/current`: Datos de la copropiedad activa.
- `GET /audit`: Registro inmutable de auditoría del tenant.
- `GET/POST /platform/companies`, `GET/POST /platform/tenants`: Gestión de nivel Super Admin.

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
- `GET/PUT /billing/settings`, `POST /billing/generate-monthly-charges`
- `GET /billing/portfolio`: Listado general de saldos y morosidad.
- `POST /payments`: Registro de pagos con `Idempotency-Key`.
- `POST /payments/{id}/reverse`: Reversión justificada de pagos.
- `GET /my/account-statement`: Estado de cuenta consolidado del residente.
- `GET /billing/ledger`: Libro mayor de movimientos contables inmutable.
- `POST /reconciliation/imports`: Carga asíncrona de extractos bancarios.
- `GET /reconciliation/imports/{id}`, `GET /reconciliation/rows`
- `POST /reconciliation/rows/{id}/apply`: Conciliación manual de filas de extracto.

### Wompi y Certificados PDF (Fase 3c)
- `POST /payments/online/checkout`: Creación de intención de pago en línea.
- `POST /payments/online/webhook/wompi`: Webhook seguro con verificación de hash SHA-256 e idempotencia.
- `POST /certificates/clearance`: Generación de Paz y Salvo en PDF con OpenPDF.
- `POST /certificates/statement`: Generación de Estado de Cuenta oficial en PDF.
- `GET /public/certificates/verify/{code}`: Validación pública del certificado mediante código y hash de integridad.

### Zonas Comunes, Convivencia y Asambleas (Fase 4)
- `GET/POST /common-areas`, `GET /common-areas/{id}/availability?date=`
- `POST/GET /my/reservations`, `POST /my/reservations/{id}/cancel`
- `GET /reservations`, `POST /reservations/{id}/approve`, `POST /reservations/{id}/reject`
- `POST/GET /my/pqrs`, `GET /pqrs`, `POST /pqrs/{id}/messages` (soporta `isInternal`), `GET /pqrs/metrics/sla`
- `POST/GET /communications`, `PATCH /communications/{id}/publish`, `GET /my/communications`
- `GET/PUT /notifications/settings`, `POST /notifications/portfolio-reminders`, `GET /notifications/logs`
- `POST /documents`, `GET /documents`, `GET /my/documents`, `GET /my/documents/{id}/download`
- `POST/GET /assemblies`, `POST /assemblies/{id}/check-in`, `GET /assemblies/{id}/quorum`
- `POST/GET /assemblies/{id}/topics`, `POST /assemblies/topics/{id}/open-vote`, `POST /assemblies/topics/{id}/close-vote`
- `POST /my/assemblies/topics/{id}/vote` (ponderado por coeficiente de copropiedad según Ley 675).

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

---

## 4. Cómo Correr y Verificar el Sistema

### 4.1 Comandos de Despliegue (PowerShell)
```powershell
# 1. Asegurar archivo .env
cp .env.example .env

# 2. Levantar los contenedores de Docker (Postgres, Redis, Backend, Nginx)
docker compose up -d --build vecindad_nginx

# 3. Ver logs del backend para confirmar arranque limpio y migraciones Flyway
docker logs vecindad_backend --tail 50

# 4. Ejecutar la suite completa de pruebas unitarias
cd backend
mvn test
# Resultado esperado: Tests run: 83, Failures: 0, Errors: 0, Skipped: 0 -> BUILD SUCCESS
cd ..

# 5. Ejecutar la suite de smoke tests integral (20 secciones de prueba end-to-end)
powershell -ExecutionPolicy Bypass -File .\scripts\smoke-test.ps1
# Resultado esperado: Las 20 secciones en verde y mensaje final "TODO OK"
```

### 4.2 Credenciales de Demostración
- **Contraseña universal de demo:** `Demo#2026!` (configurable vía `APP_SEED_PASSWORD`).
- **Super Administrador de Plataforma:** `superadmin@codevam.local`
- **Administrador Multi-copropiedad:** `admin@vecindad.local` (acceso a `demo_norte` y `demo_sur`).
- **Portería Norte:** `portero@demo-norte.local`
- **Contador Norte:** `contador@demo-norte.local`
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

---

## 6. Siguiente Paso Obligatorio: Fase 6 (Frontend)

El backend está **100% completo, probado y verificado**. La siguiente etapa es la construcción del **Frontend**:

- **Ubicación:** Carpeta `./frontend`.
- **Tecnologías:** Angular 18+ con Ionic 8 Framework, Capacitor 6+ y soporte PWA.
- **Contrato de Build Docker:** El servicio `vecindad_frontend` en `docker-compose.yml` (bajo el perfil `frontend`) compila el proyecto y monta la carpeta `dist/vecindad/browser` en un servidor Nginx ligero.
- **Requisitos de UI/UX:**
  - Branding dinámico obtenido del endpoint `/api/v1/public/config`.
  - Selector de copropiedad para usuarios con membresías múltiples.
  - Vistas adaptadas a los distintos roles:
    1. **Super Administrador:** Gestión de empresas y copropiedades.
    2. **Administración:** Dashboard ejecutivo, inmuebles, cartera, proveedores, mantenimiento, asambleas y reportes.
    3. **Portería:** Interfaz táctil y rápida para control de acceso de vehículos, visitantes (lector QR / walk-in), paquetería y novedades de seguridad.
    4. **Portal del Residente (App Móvil / PWA):** Estado de cuenta, pagos en línea (Wompi), autorizaciones de visitas, reserva de zonas comunes, radicación de PQRS, comunicados y votación en asambleas.
