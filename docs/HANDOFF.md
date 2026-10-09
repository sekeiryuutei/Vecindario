# VECINDAD — Documento de traspaso (estado al cierre de la Fase 4)

Plataforma SaaS multi-tenant de administración de copropiedades (Colombia). Marca: **Vecindad** · Empresa: **CodeVam** (ambos configurables por `.env`; nunca hardcodeados).
Este documento permite continuar el trabajo en otra conversación/cuenta sin perder contexto. Léelo completo antes de escribir código.

## 1. Stack y estructura
- Backend: Java 21, Spring Boot 3.3.5, Maven, PostgreSQL 16 (**un schema por copropiedad**), Flyway, Redis, JWT. Arquitectura hexagonal por módulo (`domain` / `application` + `port/out` / `adapter/in/web` / `adapter/out`).
- Infra: Docker Compose, Nginx. Almacenamiento local desacoplado vía `StoragePort` (`LocalStorageAdapter` en `/app/data` o S3).
- Frontend (pendiente, ZIP aparte): Angular + Ionic + Capacitor + PWA; el compose ya tiene el servicio `vecindad_frontend` bajo `--profile frontend` (contrato: proyecto Angular `vecindad` → `dist/vecindad/browser`).
- Estructura: `vecindad/{backend,frontend,docker,scripts,docs,docker-compose.yml,.env.example,README.md}`.
- Paquete Java base: `com.codevam.vecindad` → `shared`, `config`, `identity`, `tenancy`, `audit`, `properties`, `people`, `vehicles`, `visitors`, `parcels`, `incidents`, `securitysummary`, `billing`, `reconciliation`, `wompi`, `certificates`, `reservations`, `pqrs`, `communications`, `notifications`, `storage`, `documents`, `assemblies`, `bootstrap`.

## 2. Cómo correrlo y probarlo (Windows / PowerShell)
```powershell
cp .env.example .env                       # cambiar claves; JWT_SECRET >= 32 caracteres
docker compose up -d --build vecindad_nginx   # levanta backend + postgres + redis + nginx
docker compose logs vecindad_backend --tail 60
powershell -ExecutionPolicy Bypass -File .\scripts\smoke-test.ps1   # debe terminar en TODO OK (16 secciones)
cd backend; mvn test                       # 68 unit tests pasando
```
Swagger: `http://localhost:48124/swagger-ui.html`. Usuarios demo y contraseña: ver README (`Demo#2026!`).

## 3. Estado por fase
| Fase | Contenido | Estado |
|---|---|---|
| 1 | Docker, multi-tenancy schema-per-tenant, Flyway, JWT+refresh rotativo, RBAC con permisos por copropiedad, auditoría, membresías con historial, super admin, inmuebles | **Verificada** (smoke test) |
| 2a | Personas N:N, vehículos, límites configurables, regla «vehículo dentro», portería (entrada/salida atómicas), alertas | **Verificada** |
| 2b | Visitantes (QR de un solo uso, autorización en tiempo real), paquetes, novedades, resumen de seguridad | **Verificada** |
| 3a | Cartera: cargos, facturación masiva, imputación configurable, pagos idempotentes/reversibles, notas, intereses, estados de cuenta, libro inmutable | **Verificada** |
| 3b | Conciliación bancaria CSV/XLSX asíncrona, perfiles por banco, matching con puntaje, duplicados, revisión manual, reversión | **Verificada** |
| 3c | Wompi + certificados PDF con OpenPDF y verificación pública con hash | **Verificada** |
| 4 | Zonas comunes y reservas (bloqueo por mora, reglas horarias), PQRS con SLA (24h para ruido) y notas privadas, comunicados segmentados, notificaciones multicanal, almacenamiento `StoragePort` y repositorio de documentos, asambleas y votación ponderada Ley 675 | **Verificada (16/16 secciones TODO OK)** |
| 5 | Mantenimiento, proveedores, presupuesto administrativo, reportes, frontend Angular + Ionic + Capacitor + PWA | Pendiente (siguiente paso) |

## 4. Decisiones de arquitectura que hay que respetar
- **Tenant**: sale SIEMPRE del JWT (`tid`) + membresía verificada en BD en cada petición (`JwtAuthenticationFilter`). Nunca de parámetros/headers/cuerpo. Rol y permisos se leen de BD (no van en el token) → revocación inmediata.
- **Datos globales** (`public`): JDBC con nombres `public.tabla`. **Datos de tenant**: `properties` usa JPA; todo lo posterior usa `TenantJdbc` (marcador `{s}` → schema del tenant activo, validado con `SchemaNames`). Sin tenant en contexto → `TENANT_NOT_SELECTED`.
- **Operaciones críticas = una sola sentencia SQL atómica** (CTE `UPDATE … RETURNING` + `INSERT`): entrada/salida de vehículos, ingreso por QR, límites de vehículos. Estados con `UPDATE` condicional.
- **Finanzas**: `FinanceTx` (transacción JDBC privada, no registrada como bean para no desactivar la de JPA) + `lockUnit` (`SELECT … FOR UPDATE`) por inmueble. Dinero siempre `NUMERIC`/`BigDecimal`. `CHECK (amount + adjusted_amount >= paid_amount)` impide sobrepago en BD. Libro de movimientos inmutable (trigger). Invariante: saldo del libro = Σ pendiente de cargos − saldo a favor.
- **Idempotencia**: `Idempotency-Key` en pagos (200 en reintento, 201 la primera vez); llaves `bank:<hash>:<n>` en conciliación; webhook de Wompi usa `wompi-<transactionId>`.
- **Pagos nunca se editan**: se revierten con motivo. Cargos anulables solo sin pagos. Notas crédito/débito.
- **Auditoría**: `AuditService.log/logAs` (REQUIRES_NEW), append-only; nunca guarda claves con pass/secret/token.
- **API**: JSON con `Content-Type: application/json` SIEMPRE (también POST sin datos → `{}`); error estándar `{code,message,timestamp,path,traceId,details}`; paginación `{content,page,size,totalElements,totalPages}` (máx. 100). 404 (no 403) cuando un residente pide algo ajeno.
- **Migraciones**: NUNCA editar una ya aplicada (checksum). Siguiente global: `V9`; siguiente de tenant: `V8`. Los `TenantMigrationRunner` las aplican a todas las copropiedades al arrancar.
- **Seeds demo** idempotentes por fase (`DemoDataPhase2Runner`, `2b`, `3`, `4`), solo si `APP_SEED_ENABLED=true` y no es producción.
- **Trampas conocidas**: en text blocks de Java el espacio antes de `"""` se pierde (usar concatenación de strings para `SELECT ` + columnas); en PowerShell usar `powershell -ExecutionPolicy Bypass -File .\scripts\smoke-test.ps1`.

## 5. Reglas de entrega que fijó el usuario (obligatorias)
1. Entregar **UN solo ZIP** con el proyecto bajo `vecindad/` (sin doble carpeta raíz), listo para descomprimir sobre el existente y **reemplazar** archivos.
2. Si se elimina/renombra/mueve algo existente → lista explícita de rutas a borrar a mano ANTES de descomprimir (hasta hoy: ninguna).
3. El frontend va en **otro ZIP** (`frontend/`, sin `node_modules/`, `dist/`, ni `package-lock.json` inventado); comparte `docker-compose.yml`/`.env` del raíz.
4. UI en español, código en inglés. Sin placeholders ni «próximamente» en módulos especificados; integraciones externas con adapter real + mocks solo para tests; **no inventar APIs** (Wompi, WhatsApp, cámaras…).
5. Comandos pensados para **PowerShell** (smoke test `.ps1`).
6. Puertos configurables por `.env` (ejemplos 48120–48126), contenedores `vecindad_*`, nada de rutas absolutas.

## 6. Lo que falta: Fase 5
- Mantenimiento preventivo/correctivo y órdenes de trabajo (`com.codevam.vecindad.maintenance`).
- Proveedores y contratistas (`com.codevam.vecindad.suppliers`).
- Presupuesto y contabilidad administrativa (`com.codevam.vecindad.budget`).
- Reportes con exportación (CSV, XLSX, PDF) y dashboards analíticos.
- Integración de cámaras / LPR con interfaces desacopladas.
- **Frontend Angular + Ionic + Capacitor + PWA**: pantallas y navegación para administración y residentes.

