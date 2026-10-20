# Pruebas

## Comandos

```bash
cd backend
mvn -B test         # 118 pruebas unitarias (sin Docker)
mvn -B verify       # 51 IT con PostgreSQL real + JaCoCo (requiere Docker)

cd ../frontend
npm ci
npx playwright install chromium
npm run build       # compilación estricta de Angular + service worker PWA
E2E_BASE_URL=http://localhost:48124 E2E_PASSWORD='Demo#2026!' npm run test:e2e
```

Con el sistema levantado y seed activo:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\smoke-test.ps1
```

El smoke test recorre 23 secciones: salud, login multi-copropiedad, aislamiento, portería, visitantes, cartera,
conciliación, Wompi, certificados, reservas, PQRS, comunicados, documentos, asambleas, proveedores, mantenimiento,
presupuesto, reportes, autogestión, nómina y WhatsApp.
La app nativa se sincroniza con `APP_PUBLIC_URL` HTTPS y `APP_NAME`:

```powershell
$env:APP_PUBLIC_URL='https://app.ejemplo.com'
$env:APP_NAME='Mi Copropiedad'
npm run cap:sync
```

## Unitarias (118)

- `SchemaNamesTest`, `TenantContextTest`: saneamiento de identificadores y contexto de tenant.
- `JwtServiceTest`: firma, expiración, llave ajena, secreto corto o de desarrollo en producción.
- `ProductionConfigValidatorTest`: aceptación de una configuración productiva completa y rechazo conjunto de URL
  insegura, seed, Swagger, CORS local, secretos débiles, SMTP ausente, Wompi de pruebas y storage efímero.
- `PublicConfigControllerTest`: configuración pública y manifest PWA con el nombre de marca configurado.
- `PasswordPolicyAndTokensTest`: política de contraseñas y tokens opacos.
- `DocumentServiceSecurityTest`: extensiones/MIME permitidos, path traversal y roles sin acceso.
- `PlatformAdminBootstrapTest`: creación idempotente del primer superadministrador, rechazo de elevación de cuentas
  operativas y validación de configuración.
- `WompiServiceTest`, `WompiWebhookSecurityTest`: firma, comparación en tiempo constante, monto, referencia, idempotencia,
  eventos desconocidos y selección del tenant antes de registrar el pago.
- `PaymentAllocatorTest`: orden configurable, abonos parciales, sobrante y 300 casos aleatorios.
- `StatementRowMapperTest`, `MatchingEngineTest`: parseo de extractos y motor de conciliación.
- `ReservationServiceTest`, `PqrServiceTest`, `AssemblyServiceTest`, `BudgetServiceTest`, `MaintenanceServiceTest`,
  `SupplierServiceTest`, `ReportServiceTest`, `CameraServiceTest`, `CertificatePdfTest`.
- `PayrollServiceTest`: parámetros, novedades, aportes/provisiones, generación, recalculo, pago, anulación y numeración
  versionada al regenerar un periodo anulado.
- `TenantAdminServiceTest`: edición de datos y límites de los canales autogestionables.
- `WhatsAppInboundServiceTest`: firma, idempotencia, resolución de teléfono/persona/copropiedad y respuesta del asistente.

## Integración (*IT)

Requieren Docker. Usan un PostgreSQL 16 real compartido por `AbstractIntegrationTest`:

- `TenantIsolationIT`: filas solo en su schema; ignorar `tenantId`/`X-Tenant-Id`; GET por ID de otro tenant = 404;
  `select-tenant` ajeno = 403; token válido con `tid` sin membresía = 403; JWT falsificado = 401; membresía revocada y
  tenant suspendido surten efecto inmediato; residente sin acceso a inmuebles; auditoría acotada al tenant;
  administrador de tenant no entra al área de plataforma.
- `AuthFlowIT`: error genérico, bloqueo tras 5 fallos, rotación y reutilización de refresh, logout, cambio de
  contraseña, recuperación de un solo uso, invitación/activación, roles inválidos, no auto-modificarse y permisos
  ajustados por copropiedad sin afectar a otra.
- `VehicleAccessIT`: vehículo dentro no se modifica ni elimina hasta registrar la salida; entrada duplicada, vehículo
  bloqueado, placa desconocida y salida sin entrada se rechazan y generan alertas; 6 entradas simultáneas → exactamente
  1; límites por tipo e inmueble; placa única; el residente solo ve/toca lo suyo; aislamiento entre copropiedades.
- `VisitorsPackagesIT`: QR de un solo uso, concurrencia (6 lecturas → 1 ingreso), autorización en tiempo real, paquetes,
  novedades y resumen de seguridad con permisos y aislamiento.
- `BillingIT`: ejemplo de imputación, idempotencia y concurrencia de pagos, reversión, libro inmutable, notas,
  facturación masiva, intereses y permisos por rol.
- `ReconciliationIT`: CSV real asíncrono, coincidencias, ambiguas, duplicados, revisión manual, reversión, perfiles
  bancarios, archivos inválidos y aislamiento.

Las asambleas, reservas, PQRS, presupuesto, mantenimiento, proveedores, reportes y certificados cuentan con pruebas
unitarias; sus flujos HTTP completos se validan en el smoke test.

## Frontend y E2E

`npm run build` valida plantillas estrictas, rutas, guards, tipado de modelos, PWA y presupuesto de bundles.
`APP_PUBLIC_URL=... APP_NAME=... npm run cap:sync` valida además la sincronización Android/iOS y escribe la API absoluta
en `runtime-config.json`.

Playwright cubre cinco flujos con `E2E_BASE_URL` (por defecto `http://localhost:48124`) y la contraseña demo:
administrador, residente, portería y plataforma, incluido el cambio de copropiedad. En CI se ejecuta después de levantar
Docker; localmente requiere el stack healthy y Chromium instalado. Los reportes quedan en `frontend/playwright-report/`.

`mvn -B verify` genera `backend/target/site/jacoco/` y falla si la cobertura de líneas baja de 45%. La medición actual es
53,57% de líneas, 42,84% de ramas y 58,66% de métodos.

## Verificación de producción

Se validó un arranque limpio con `SPRING_PROFILES_ACTIVE=production`, PostgreSQL y Redis nuevos, sin seed: health `UP`,
Flyway hasta `V12` global y `V9` por tenant, `/v3/api-docs` con `404`, cero tenants y usuarios.
`ProductionConfigValidatorTest` cubre la aceptación de una configuración completa y el rechazo de URL insegura, seed,
Swagger, CORS local, secretos débiles, secretos Wompi iguales, SMTP ausente, Wompi de pruebas, WhatsApp incompleto y
storage efímero.

El webhook de Wompi se probó con eventos firmados; el checkout de sandbox carga, pero la finalización del pago requiere el
secreto de integridad correcto del panel de Wompi (ver `docs/HANDOFF.md`, pendientes externos).

También se verificó CORS real: preflight desde `https://localhost`, `capacitor://localhost` e `ionic://localhost`
devuelve `Access-Control-Allow-Origin`; un origen `http://localhost:3000` se rechaza. `npm audit --omit=dev` no reporta
vulnerabilidades de runtime.
