# Pruebas

## Comandos

```bash
cd backend
mvn -B test         # 157 pruebas unitarias (sin Docker)
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

El smoke test recorre 24 secciones: salud, login multi-copropiedad, aislamiento, portería, visitantes, cartera,
conciliación, Wompi, certificados, reservas, PQRS, comunicados, documentos, asambleas, proveedores, mantenimiento,
presupuesto, reportes, autogestión, nómina, WhatsApp y mascotas.
La app nativa se sincroniza con `APP_PUBLIC_URL` HTTPS y `APP_NAME`:

```powershell
$env:APP_PUBLIC_URL='https://app.ejemplo.com'
$env:APP_NAME='Mi Copropiedad'
npm run cap:sync
```

## Unitarias (157)

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
  `SupplierServiceTest`, `ReportServiceTest`, `CameraServiceTest`, `CertificatePdfTest`, `PetServiceTest`.
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

Playwright cubre diez flujos con `E2E_BASE_URL` (por defecto `http://localhost:48124`) y la contraseña demo:
administrador, residente, portería y plataforma, incluido el cambio de copropiedad, el alta/baja de mascotas por
administración y residente, y el alta de mascotas propias. En CI se ejecuta después de levantar Docker; localmente
requiere el stack healthy y Chromium instalado. Los reportes quedan en `frontend/playwright-report/`.

`mvn -B verify` genera `backend/target/site/jacoco/` y falla si la cobertura de líneas baja de 45%. La medición actual es
53,17% de líneas, 42,37% de ramas y 58,16% de métodos.

## Recorrido manual por pantalla

Base local: `http://localhost:48124`. Contraseña demo: `Demo#2026!`.

| Rol | Usuario | Alcance |
|---|---|---|
| Superadmin | `superadmin@vecindad.local` | Plataforma |
| Administrador | `admin@vecindad.local` | Demo Norte y Demo Sur |
| Administrador Sur | `admin.sur@demo-sur.local` | Demo Sur |
| Secretaría | `secretaria@demo-norte.local` | Operación administrativa |
| Contador | `contador@demo-norte.local` | Cartera y pagos |
| Consejo | `consejo@demo-norte.local` | Consulta y aprobación |
| Portería | `portero@demo-norte.local` | Control de acceso |
| Propietario | `propietario@demo-norte.local` | Portal del residente |

### Públicas

| Pantalla | Ruta | Prueba mínima |
|---|---|---|
| Login | `/login` | Entrar con cada rol; una contraseña incorrecta debe mostrar error genérico |
| Recuperación | `/recuperar` | Solicitar enlace; abrir el enlace recibido y cambiar la contraseña |
| Restablecer | `/restablecer?token=...` | Validar token de un solo uso y política de contraseña |
| Selección de copropiedad | `/seleccionar-copropiedad` | Con `admin@vecindad.local`, cambiar entre Demo Norte y Demo Sur |
| Verificación de certificado | `/verificar-certificado` | Pegar el código de un certificado emitido y comprobar validez |
| Resultado Wompi | `/pagos/wompi/resultado?reference=...` | Consultar una intención; sin redirect público se usa la referencia generada |

### Administración

| Pantalla | Ruta | Prueba mínima |
|---|---|---|
| Inicio | `/app/inicio` | Ver indicadores y accesos; deben cambiar al cambiar de copropiedad |
| Propiedades | `/app/propiedades` | Listar, crear/editar inmueble y revisar relaciones |
| Personas | `/app/personas` | Crear/editar persona y vincular una cuenta de usuario |
| Vehículos | `/app/vehiculos` | Crear vehículo; intentar editar uno que está dentro debe fallar |
| Cartera | `/app/cartera` | Ver cartera, estado de cuenta, crear cargo y ejecutar facturación |
| Pagos | `/app/pagos` | Registrar pago, ver imputaciones y revertir con motivo |
| Conciliación | `/app/conciliacion` | Subir CSV, revisar filas, conciliar manualmente y revertir |
| Certificados | `/app/certificados` | Emitir certificado, descargar PDF y verificar el código |
| Reservas | `/app/reservas` | Configurar zona común, crear reserva y aprobar/cancelar |
| PQRS | `/app/pqrs` | Crear, responder, cambiar estado y revisar SLA |
| Comunicados | `/app/comunicados` | Publicar comunicado y comprobar lectura del residente |
| Notificaciones | `/app/notificaciones` | Revisar configuración y logs; WhatsApp está deshabilitado temporalmente |
| Documentos | `/app/documentos` | Subir, listar, descargar y restringir por rol |
| Asambleas | `/app/asambleas` | Crear asamblea, agenda, asistencia, votación y acta |
| Proveedores | `/app/proveedores` | Crear proveedor, contrato y póliza |
| Mantenimiento | `/app/mantenimiento` | Crear activo, plan y orden de trabajo |
| Presupuesto | `/app/presupuesto` | Crear presupuesto, rubros y consultar ejecución |
| Nómina | `/app/nomina` | Parámetros, empleado, periodo, calcular, pagar, anular y exportar CSV |
| Reportes | `/app/reportes` | Generar reporte y exportar CSV/PDF |
| Auditoría | `/app/auditoria` | Ver eventos de las acciones anteriores |
| Usuarios | `/app/usuarios` | Invitar usuario, asignar rol y revocar acceso |
| Roles | `/app/roles` | Revisar permisos por rol y su efecto en el menú |
| Configuración | `/app/configuracion` | Editar datos, activar/desactivar canales y revisar ajustes |

### Portería

| Pantalla | Ruta | Prueba mínima |
|---|---|---|
| Control de acceso | `/app/porteria/control` | Buscar placa, registrar entrada y salida; duplicados deben rechazarse |
| Visitantes | `/app/porteria/visitantes` | Validar QR, registrar ingreso, crear visitante sin invitación y autorizar |
| Paquetes | `/app/porteria/paquetes` | Recibir paquete y entregarlo; una segunda entrega debe fallar |
| Novedades | `/app/porteria/novedades` | Registrar novedad y verla en administración |

### Residente

| Pantalla | Ruta | Prueba mínima |
|---|---|---|
| Inmuebles | `/app/residente/inmuebles` | Ver los inmuebles propios y sus relaciones |
| Vehículos | `/app/residente/vehiculos` | Crear/editar un vehículo propio |
| Estado de cuenta | `/app/residente/estado-cuenta` | Ver movimientos y crear intención Wompi desde `$1.500` |
| Certificados | `/app/residente/certificados` | Solicitar/descargar certificado y verificar su código |
| Visitas | `/app/residente/visitas` | Crear invitación con QR y autorizar una visita pendiente |
| Reservas | `/app/residente/reservas` | Reservar una zona común y cancelar si aplica |
| Selección de copropiedad | `/seleccionar-copropiedad` | Revisar el panel general con las métricas de todas las copropiedades asignadas y elegir una |
| PQRS | `/app/residente/pqrs` | Crear PQRS y seguir la respuesta |
| Comunicados | `/app/residente/comunicados` | Leer y marcar comunicados |
| Asambleas | `/app/residente/asambleas` | Registrar asistencia y votar |
| Documentos | `/app/residente/documentos` | Descargar documentos permitidos |
| Paquetes | `/app/residente/paquetes` | Ver paquetes recibidos/entregados |

### Plataforma

| Pantalla | Ruta | Prueba mínima |
|---|---|---|
| Dashboard | `/app/plataforma` | Ver métricas globales |
| Empresas | `/app/plataforma/empresas` | Crear/editar empresa |
| Copropiedades | `/app/plataforma/copropiedades` | Crear/editar copropiedad, sus feature flags y las integraciones (Wompi/IA) por copropiedad |
| Auditoría global | `/app/plataforma/auditoria` | Ver eventos de plataforma |

### Integraciones por copropiedad

1. Entrar como `superadmin@vecindad.local`, abrir **Copropiedades → Gestionar** y verificar que la sección
   *Integraciones de la copropiedad* indica que Wompi y la IA usan la configuración global.
2. Guardar unas credenciales de Wompi de prueba (`pub_…`, `prv_…`, secretos de 16 caracteres o más y distintos entre
   sí). El panel debe mostrar la copropiedad como configurada y los campos de secretos deben quedar vacíos.
3. Comprobar en la base de datos que la fila de `public.tenant_integrations` no contiene el texto plano de las llaves.
4. Crear una reserva con pago en Demo Norte y verificar que la URL de checkout usa la llave pública de esa copropiedad.
5. Pulsar *Volver a la configuración global* y verificar que la copropiedad vuelve a heredar los valores del entorno.
6. Repetir el guardado con dos secretos iguales y confirmar que la API responde `REUSED_WOMPI_SECRET`.

### Pagos en línea por copropiedad

- Demo Norte tiene `ONLINE_PAYMENTS=true`: el propietario ve el botón de pago y obtiene una URL de checkout de Wompi.
- Demo Sur tiene `ONLINE_PAYMENTS=false`: el propietario ve el aviso de que los pagos en línea no están habilitados y la
  API responde `403 ONLINE_PAYMENTS_DISABLED` (no es un problema de permisos del rol).

### Desglose de la tarifa por pago en línea

1. Entrar como `propietario@demo-norte.local` en Demo Norte y abrir *Estado de cuenta*.
2. Escribir un valor (por ejemplo `25000`) y verificar que aparecen *Valor a pagar*, *Comisión de la pasarela*,
   *Tarifa de la plataforma* y *Total a pagar*.
3. Comprobar que la suma coincide con el total y que `GET /api/v1/my/payments/online/quote?amount=25000` devuelve los
   mismos valores: `netAmountInCents` 2500000, `platformFeeInCents` 200000 y `totalInCents` 2873930.
4. Crear la intención y verificar que la URL de checkout cobra el total y que la referencia queda en estado `PENDING`.
5. Con la firma del webhook, confirmar que el pago acreditado en cartera es **el valor de la deuda** y no el total.


### Flujo integrado de 10 minutos

1. Entrar como administrador, cambiar a Demo Norte y crear una visita desde el portal del propietario.
2. Entrar como portería, validar el QR, registrar el ingreso y entregar un paquete.
3. Como administrador, crear un cargo; como propietario, pagarlo y revisar el estado de cuenta.
4. Crear una PQRS y una reserva; responder y aprobar desde administración.
5. Emitir un certificado, descargarlo y verificarlo desde la página pública.
6. Subir un documento y comprobarlo desde el portal del residente.
7. Crear una asamblea, registrar asistencia y votar.
8. Generar una corrida de nómina, pagarla y exportar el CSV.
9. Revisar auditoría y cerrar sesión.

## Verificación de producción

Se validó un arranque limpio con `SPRING_PROFILES_ACTIVE=production`, PostgreSQL y Redis nuevos, sin seed: health `UP`,
Flyway hasta `V12` global y `V9` por tenant, `/v3/api-docs` con `404`, cero tenants y usuarios.
`ProductionConfigValidatorTest` cubre la aceptación de una configuración completa y el rechazo de URL insegura, seed,
Swagger, CORS local, secretos débiles, secretos Wompi iguales, SMTP ausente, Wompi de pruebas, WhatsApp incompleto y
storage efímero.

El flujo Wompi se verificó de extremo a extremo en sandbox: checkout real con tarjeta de prueba, transacción aprobada,
webhook firmado, intención `APPROVED` y pago reflejado en el estado de cuenta. El webhook público real requiere un dominio
HTTPS; en local se valida con un evento firmado (ver `docs/HANDOFF.md`).

También se verificó CORS real: preflight desde `https://localhost`, `capacitor://localhost` e `ionic://localhost`
devuelve `Access-Control-Allow-Origin`; un origen `http://localhost:3000` se rechaza. `npm audit --omit=dev` no reporta
vulnerabilidades de runtime.
