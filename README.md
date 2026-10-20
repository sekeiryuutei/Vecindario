# VECINDAD — plataforma de administración de copropiedades

Plataforma SaaS multi-copropiedad para portería, cartera, convivencia y administración. Nombre y empresa visibles se
configuran con `APP_NAME` y `APP_COMPANY`; ningún texto de la interfaz los hardcodea.

> **Estado: fases 1 a 6 completadas, más autogestión, nómina, WhatsApp y calidad automatizada.** Backend Spring Boot,
> frontend Angular/Ionic/PWA, aplicación Capacitor para Android/iOS e infraestructura Docker implementados. La suite del
> backend (118 unitarias + 51 de integración), la compilación del frontend y Playwright (5/5) se ejecutan sin errores.

## Contenido

- Multi-tenancy con un schema PostgreSQL por copropiedad, Flyway, JWT con refresh rotativo, RBAC configurable por
  copropiedad, auditoría append-only y aislamiento validado en cada petición.
- Personas, inmuebles, parqueaderos, vehículos y límites; control de acceso vehicular y peatonal; visitantes con QR de
  un solo uso; paquetería y novedades de portería.
- Cartera con cargos, facturación masiva idempotente, pagos, notas crédito/débito, intereses, estados de cuenta, libro
  inmutable y conciliación bancaria CSV/XLSX.
- Wompi con firma de webhook, idempotencia y bloqueo pesimista; monto mínimo de `$1.500 COP`; certificados PDF con
  verificación pública.
- Reservas de zonas comunes, PQRS con SLA, comunicados, notificaciones por correo y WhatsApp, registro de entregas,
  documentos con almacenamiento persistente, asambleas con quórum y votación ponderada.
- Autogestión de datos de la copropiedad y canales; nómina parametrizable con aportes, provisiones, pago, anulación y CSV.
- WhatsApp Cloud API entrante con firma e idempotencia y respuestas asistidas con proveedores intercambiables y respaldo
  local; la interfaz no expone marcas ni proveedores.
- Proveedores y contratos, mantenimiento, presupuesto, reportes CSV/PDF y cámaras LPR desacopladas.
- Frontend standalone con Angular 20, Ionic 8 y Capacitor 8: portal de administración, portería, residente, superadmin
  de plataforma, páginas públicas, PWA instalable y proyecto móvil.

Documentación detallada: [arquitectura](docs/ARCHITECTURE.md), [API](docs/API.md), [seguridad](docs/SECURITY.md),
[despliegue](docs/DEPLOYMENT.md), [pruebas](docs/TESTING.md), [hoja de ruta](docs/ROADMAP.md) y
[traspaso](docs/HANDOFF.md).

## Requisitos

- Docker 24+ con Docker Compose v2 (`docker compose version`).
- Para desarrollo local sin contenedores: JDK 21, Maven 3.9+, Node.js 22.12+ (o 24) y npm.
- Para compilar Android/iOS: Android Studio / Xcode y `npx cap sync` después de cada build web.

## Inicio rápido con Docker

**1. Crea el archivo de entorno.**

```bash
cp .env.example .env
```

**2. Edita `.env`.** Como mínimo cambia `DATABASE_PASSWORD`, `REDIS_PASSWORD`, `JWT_SECRET`
(`openssl rand -base64 48`) y, si aplica, los puertos ocupados. Para desarrollo deja `APP_ENV=development` y
`APP_SEED_ENABLED=true`; el seed carga los usuarios demo una sola vez.

**3. Levanta la plataforma completa.**

```bash
docker compose up -d --build
docker compose ps
docker compose logs -f vecindad_backend
```

El arranque aplica Flyway en el schema global y en cada copropiedad, y espera los healthchecks antes de habilitar Nginx.
La primera compilación descarga dependencias de Maven y npm.

**4. Accede** (puertos por defecto):

| Servicio | URL |
|---|---|
| Aplicación web (Nginx) | http://localhost:48124/ |
| API vía Nginx | http://localhost:48124/api/v1 |
| Swagger UI (solo desarrollo) | http://localhost:48124/swagger-ui.html |
| Health | http://localhost:48124/actuator/health |
| Frontend directo | http://127.0.0.1:48120/ |
| Backend directo | http://127.0.0.1:48121/ |

PostgreSQL, Redis, el backend y el frontend se publican solo en `127.0.0.1`; únicamente Nginx escucha en todas las
interfaces. MinIO es opcional (`docker compose --profile storage up -d`) y no es necesario con el almacenamiento local.

**5. Usuarios demo** (contraseña `APP_SEED_PASSWORD`, por defecto `Demo#2026!`). Solo existen con
`APP_SEED_ENABLED=true` y `APP_ENV` distinto de `production`.

| Correo | Rol |
|---|---|
| superadmin@vecindad.local | SUPER_ADMIN_PLATFORM |
| admin@vecindad.local | ADMINISTRADOR (Demo Norte y Demo Sur) |
| admin.sur@demo-sur.local | ADMINISTRADOR (Demo Sur) |
| secretaria@demo-norte.local | SECRETARIA_ADMINISTRACION |
| contador@demo-norte.local | CONTADOR |
| consejo@demo-norte.local | CONSEJO |
| portero@demo-norte.local | PORTERO |
| propietario@demo-norte.local | PROPIETARIO (Norte y Sur) |

## Desarrollo del frontend

```bash
cd frontend
npm ci
npm start              # http://localhost:4200 con proxy a /api
npm run build          # build de producción + service worker PWA en dist/vecindad/browser
npm run cap:sync       # build + sync nativo (requiere APP_PUBLIC_URL y APP_NAME)
npm run cap:android    # abre Android Studio
npm run cap:ios        # abre Xcode (macOS)
```

El proxy de desarrollo (`proxy.conf.json`) apunta a `http://localhost:8080`; cámbialo si tu backend local usa otro
puerto. En producción el frontend llama a `/api/v1` en el mismo origen. Los iconos PWA se regeneran con
`powershell -File scripts/generate-icons.ps1`.

Los proyectos `android/` e `ios/` ya están generados. Para compilar la app nativa define la URL pública y el nombre
visible antes de sincronizar; el script escribe la API absoluta en `runtime-config.json` y actualiza el nombre del
lanzador:

```powershell
$env:APP_PUBLIC_URL='https://app.ejemplo.com'
$env:APP_NAME='Mi Copropiedad'
npm run cap:sync
```

La PWA web sigue usando `/api/v1` en el mismo origen. En el backend, `CORS_ALLOWED_ORIGINS` debe incluir
`https://localhost` (Android), `capacitor://localhost` (iOS) e `ionic://localhost` cuando se use la app nativa. La cámara
del escáner QR ya declara permisos en `AndroidManifest.xml` e `Info.plist`.

## Pruebas

```bash
cd backend
mvn -B test             # 118 pruebas unitarias, sin Docker
mvn -B verify           # 51 IT con PostgreSQL real + JaCoCo (requiere Docker)
```

El frontend se valida con `npm run build` y Playwright (5 flujos de los cuatro portales). El smoke test integral del
backend se ejecuta con el sistema levantado y seed activo:

```bash
cd frontend
npx playwright install chromium
E2E_BASE_URL=http://localhost:48124 E2E_PASSWORD='Demo#2026!' npm run test:e2e
```

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\smoke-test.ps1
```

El smoke cubre 23 secciones: salud, autenticación multi-copropiedad, aislamiento, portería, visitantes, cartera,
conciliación, Wompi, certificados, reservas, PQRS, comunicados, documentos, asambleas, proveedores, mantenimiento,
presupuesto, reportes, autogestión, nómina y WhatsApp.

## Producción

Antes de exponer el servicio:

1. `APP_ENV=production`, `APP_SEED_ENABLED=false`, `SWAGGER_ENABLED=false`, `MAIL_DEV_LOG_LINKS=false`.
2. `APP_PUBLIC_URL` y `WOMPI_REDIRECT_URL` con HTTPS público; `CORS_ALLOWED_ORIGINS` solo con orígenes HTTPS reales y,
   si se compila la app nativa, los orígenes exactos `https://localhost` y `capacitor://localhost`.
3. Claves nuevas y robustas para PostgreSQL, Redis, JWT (48+ bytes), Wompi, SMTP y WhatsApp. En Wompi, el secreto de
   integridad y el de eventos deben ser distintos; el redirect debe ser HTTPS público.
4. TLS terminado en un proxy delante de Nginx, backups programados y restauración probada.
5. `STORAGE_LOCAL_DIR` en un volumen persistente (el compose ya monta `vecindad_storage`).
6. Si se habilitan WhatsApp y respuestas del asistente, configura `WHATSAPP_VERIFY_TOKEN`, `WHATSAPP_APP_SECRET`,
   `WHATSAPP_ACCESS_TOKEN` y `WHATSAPP_PHONE_NUMBER_ID`; registra
   `https://TU_DOMINIO/api/v1/public/whatsapp/webhook`. Revisa `AI_*`/`GEMINI_*` y deja el respaldo local como última
   opción.
7. En una instalación nueva sin seed, define `APP_BOOTSTRAP_EMAIL` y `APP_BOOTSTRAP_PASSWORD` durante el primer
   arranque para crear el superadministrador de plataforma. Verifica el acceso y retira esas dos variables del entorno.

El backend **no arranca** en producción si detecta seed activo, Swagger público, secretos de ejemplo, CORS inseguro,
correo sin configurar, Wompi con llaves de prueba o secretos iguales, WhatsApp incompleto o almacenamiento no persistente.
Ver [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) y [docs/SECURITY.md](docs/SECURITY.md).

## Respaldo y restauración

```bash
./scripts/backup.sh                                  # backups/vecindad_<fecha>.sql.gz (todos los schemas)
./scripts/restore.sh backups/vecindad_<fecha>.sql.gz # DESTRUYE la base actual
```

Incluye el schema `public` y todos los schemas de copropiedad. Copia los respaldos fuera del servidor.

## Estructura

```
vecindad/
├── backend/            Spring Boot 3 (Java 21), Maven, arquitectura hexagonal por módulo
├── frontend/           Angular 20 + Ionic 8 + Capacitor 8 + PWA (android/ e ios/ nativos)
├── docker/             Nginx del borde y Dockerfile del frontend
├── scripts/            backup, restore, smoke test y utilidades
├── docs/               arquitectura, API, seguridad, despliegue, pruebas, roadmap y handoff
├── docker-compose.yml
├── .env.example
└── README.md
```

## Troubleshooting

| Síntoma | Qué hacer |
|---|---|
| **Puerto ocupado** (`port is already allocated`) | Cambia el puerto en `.env` y ejecuta `docker compose up -d`. |
| **PostgreSQL no inicia** | Revisa `docker compose logs vecindad_postgresql`. Si cambiaste `DATABASE_PASSWORD` tras el primer arranque, el volumen conserva la clave anterior: `docker compose down -v` (borra datos) o restaura la clave. |
| **Redis no inicia** | Revisa `REDIS_PASSWORD`. Si Redis cae, el login sigue funcionando con bloqueo por cuenta en base de datos; el rate limit falla abierto. |
| **Falla una migración** | Flyway aborta el arranque a propósito. Corrige y reinicia; nunca edites una migración ya aplicada, agrega una nueva `V<n>__...sql`. |
| **Backend "unhealthy" al inicio** | Es normal durante la primera migración. Sigue `docker compose logs -f vecindad_backend`. |
| **El frontend no conecta con la API** | Verifica `http://localhost:<NGINX_PORT>/api/v1/public/config` y `CORS_ALLOWED_ORIGINS`. |
| **La PWA no se actualiza** | Recarga una vez con el service worker activo; `index.html`, `ngsw.json` y `ngsw-worker.js` se sirven sin caché. |
| **Configuración de producción inválida** | El log indica cada variable que debe corregirse; el contenedor no arranca hasta cumplirla. |
| **Wompi no responde** | Usa llaves y secretos del entorno correcto, y revisa `WOMPI_API_URL` y `WOMPI_REDIRECT_URL`. Si el checkout rechaza la firma, el secreto de integridad no corresponde al panel. |
| **Wompi bloquea el checkout local** | Wompi rechaza redirects HTTP/localhost; deja `WOMPI_REDIRECT_URL` vacío en desarrollo y usa el recibo de la pasarela. |

## Git

Los commits deben usar el autor `Victor Burbano <vburbano@live.com>`. `.env`, `target/`, `node_modules/` y `backups/`
están excluidos por `.gitignore`.
