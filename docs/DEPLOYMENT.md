# Despliegue

## Arquitectura del despliegue

`docker compose` levanta cinco servicios: PostgreSQL, Redis, backend, frontend y Nginx. Solo Nginx se publica en todas
las interfaces; PostgreSQL, Redis, backend y frontend escuchan en `127.0.0.1`. En producción, un proxy TLS
(Caddy/Traefik/Nginx) debe terminar HTTPS delante de `NGINX_PORT` y enviar `X-Forwarded-Proto: https`.

El volumen `vecindad_storage` conserva documentos y certificados. Los volúmenes `vecindad_postgres_data`,
`vecindad_redis_data` y `vecindad_minio_data` sobreviven a `docker compose down`; `docker compose down -v` los borra.

## Preparación

1. Copia y edita el entorno:

   ```bash
   cp .env.example .env
   ```

2. Configura como mínimo:

   ```dotenv
   APP_ENV=production
   APP_NAME=Nombre de la copropiedad
   APP_COMPANY=Empresa administradora
   APP_PUBLIC_URL=https://app.ejemplo.com
   APP_SEED_ENABLED=false
   SWAGGER_ENABLED=false
   MAIL_DEV_LOG_LINKS=false
   MAIL_HEALTH_ENABLED=false
   CORS_ALLOWED_ORIGINS=https://app.ejemplo.com
   # Si compilas la app Capacitor, añade los orígenes exactos:
   # CORS_ALLOWED_ORIGINS=https://app.ejemplo.com,https://localhost,capacitor://localhost
   JWT_SECRET=<48+ bytes aleatorios, p. ej. openssl rand -base64 64>
   INTEGRATIONS_ENCRYPTION_KEY=<32+ bytes, p. ej. openssl rand -hex 48>
   DATABASE_PASSWORD=<16+ caracteres aleatorios>
   REDIS_PASSWORD=<16+ caracteres aleatorios>
   SMTP_HOST=smtp.proveedor.com
   SMTP_PORT=587
   SMTP_USERNAME=<usuario SMTP>
   SMTP_PASSWORD=<clave SMTP>
   SMTP_AUTH=true
   SMTP_STARTTLS=true
   MAIL_FROM=no-reply@ejemplo.com
   STORAGE_LOCAL_DIR=/app/storage
   # Solo en el primer arranque de una instalación sin seed:
   APP_BOOTSTRAP_EMAIL=superadmin@ejemplo.com
   APP_BOOTSTRAP_PASSWORD=<12+ caracteres con letras y números>
   APP_BOOTSTRAP_NAME=Administrador de plataforma
   ```

3. Si se habilitan pagos en línea, usa llaves y secretos de **producción** de Wompi:

   ```dotenv
   WOMPI_ENABLED=true
   WOMPI_PUBLIC_KEY=pub_prod_...
   WOMPI_PRIVATE_KEY=prv_prod_...
   WOMPI_EVENTS_SECRET=<secreto de eventos del panel>
   WOMPI_INTEGRITY_SECRET=<secreto de integridad del panel, distinto al de eventos>
   WOMPI_API_URL=https://production.wompi.co/v1
   WOMPI_REDIRECT_URL=https://app.ejemplo.com/pagos/wompi/resultado
   ```

   Registra en Wompi el webhook `https://app.ejemplo.com/api/v1/public/wompi/events`. La firma es obligatoria; si el
   secreto falta o no coincide, el evento se rechaza y no se registra el pago. El monto mínimo de la pasarela es
   `$1.500 COP`; la aplicación también lo valida. El redirect debe ser HTTPS público: los redirects HTTP/localhost son
   rechazados por Wompi. Para pruebas locales se puede dejar `WOMPI_REDIRECT_URL` vacío y usar el recibo de la pasarela.

4. Si se habilitan WhatsApp y respuestas automáticas, configura el webhook público y los proveedores:

   ```dotenv
   WHATSAPP_ENABLED=true
   WHATSAPP_VERIFY_TOKEN=<token aleatorio del webhook>
   WHATSAPP_APP_SECRET=<secreto de la app de Meta>
   WHATSAPP_API_VERSION=v21.0
   WHATSAPP_ACCESS_TOKEN=<token permanente>
   WHATSAPP_PHONE_NUMBER_ID=<id del número de negocio>
   WHATSAPP_BASE_URL=https://graph.facebook.com
   WHATSAPP_AI_REPLIES_ENABLED=true
   WHATSAPP_AI_INCLUDE_FINANCIAL_DATA=false
   AI_ENABLED=true
   AI_PROVIDER=gemini
   GEMINI_API_KEY=<clave del proveedor>
   GEMINI_MODEL=gemini-3.7-flash
   GEMINI_BASE_URL=https://generativelanguage.googleapis.com
   OPENAI_API_KEY=
   OPENAI_MODEL=gpt-4o-mini
   OPENAI_BASE_URL=https://api.openai.com/v1
   AI_FALLBACK_PROVIDERS=local
   AI_TIMEOUT_SECONDS=20
   ```

   En Meta configura la URL `https://app.ejemplo.com/api/v1/public/whatsapp/webhook`, el mismo verify token y la
   suscripción al campo `messages`. El backend valida `X-Hub-Signature-256` y guarda cada `message_id` una sola vez. Si
   no hay proveedor disponible, el respaldo local responde con un mensaje seguro; no incluye saldos salvo que
   `WHATSAPP_AI_INCLUDE_FINANCIAL_DATA=true`. La UI nunca muestra el proveedor ni una marca de asistencia.

El backend valida al arrancar y **aborta** si encuentra seed activo, Swagger público, CORS con `*`/HTTP o localhost de
desarrollo, contraseñas de ejemplo, JWT corto o de desarrollo, SMTP sin configurar, Wompi con llaves de prueba o
secretos de integridad/eventos iguales, WhatsApp habilitado sin verify token/app secret/token/número, proveedor de
asistencia sin su clave o almacenamiento no persistente. Los orígenes nativos exactos `https://localhost` (Android),
`capacitor://localhost` (iOS) e `ionic://localhost` se permiten cuando se compila la app.

## Despliegue

```bash
docker compose pull
docker compose build
docker compose up -d
docker compose ps
docker compose logs -f vecindad_backend
```

Espera a que todos los servicios figuren como `healthy`. La primera migración puede tardar; Flyway aplica el schema
global y luego el de cada copropiedad. Si una migración falla, el backend no arranca y el log indica el archivo y la
sentencia.

La aplicación queda disponible en `https://app.ejemplo.com` a través del proxy TLS. Verifica:

- `GET /actuator/health` → `UP` (desde el servidor).
- `GET /api/v1/public/config` → marca y configuración pública.
- `GET /manifest.webmanifest` → manifest PWA con `APP_NAME` (el Nginx del borde lo enruta al backend).
- `GET /runtime-config.json` → `{"apiBase":"/api/v1"}` para la PWA; en la app nativa el mismo archivo contiene la URL
  absoluta configurada por `cap:sync`.
- `GET /` → frontend con manifest y service worker.

## App nativa (Capacitor)

Los proyectos Android e iOS están incluidos en `frontend/android` y `frontend/ios`. Requieren Android Studio/SDK y
Xcode/CocoaPods respectivamente. Configura la URL pública HTTPS y el nombre visible; el script de sincronización escribe
la API absoluta en `runtime-config.json` y actualiza el nombre del lanzador nativo:

```powershell
cd frontend
npm ci
$env:APP_PUBLIC_URL='https://app.ejemplo.com'
$env:APP_NAME='Mi Copropiedad'
npm run cap:sync
npm run cap:android    # abre Android Studio
npm run cap:ios        # abre Xcode (macOS)
```

La PWA web no se ve afectada y mantiene `/api/v1` en el mismo origen. Para que la app nativa pueda llamar a la API,
`CORS_ALLOWED_ORIGINS` debe incluir `https://localhost` y `capacitor://localhost`. Los permisos de cámara para el
escáner QR ya están declarados. La firma y publicación en App Store/Google Play requieren cuentas y credenciales de
desarrollador; quedan fuera del despliegue automatizado.

## Actualizaciones

```bash
docker compose build
docker compose up -d
docker compose ps
docker compose logs --tail=100 vecindad_backend
```

Las migraciones Flyway se aplican automáticamente. Nunca edites una migración ya aplicada: crea una nueva
`V<n>__descripcion.sql` en `db/migration/global` o `db/migration/tenant`. El frontend se sirve como build estático; el
service worker entrega la versión nueva en la siguiente recarga.

## Respaldos

```bash
./scripts/backup.sh
./scripts/restore.sh backups/vecindad_<fecha>.sql.gz   # DESTRUYE la base actual
```

Programa `backup.sh` con cron, copia los archivos fuera del servidor y prueba la restauración periódicamente. Respalda
también el volumen `vecindad_storage` (documentos y certificados) y conserva una copia del `.env` en un gestor de
secretos.

## TLS y cabeceras

El Nginx del borde aplica CSP, `X-Frame-Options`, `X-Content-Type-Options`, `Referrer-Policy` y `Permissions-Policy`;
el Nginx del frontend refuerza las mismas cabeceras para sus respuestas estáticas. La cámara se permite para el escáner
QR. El borde agrega HSTS solo cuando recibe `X-Forwarded-Proto: https`. El proxy TLS debe enviar las cabeceras `Host`,
`X-Real-IP`, `X-Forwarded-For` y `X-Forwarded-Proto` y no debe eliminar las cabeceras de seguridad.

## Operación

- **Logs:** `docker compose logs -f <servicio>`; rotación `json-file` de 10 MB × 3 por servicio.
- **Recursos:** el backend usa `JAVA_OPTS` con `-XX:MaxRAMPercentage=75`; asigna al host al menos 2 GB de RAM y disco
  suficiente para PostgreSQL, respaldos y almacenamiento.
- **Zona horaria:** el backend opera en UTC y la lógica financiera usa `America/Bogota`; el contenedor PostgreSQL fija
  `TZ=America/Bogota`.
- **Purga de tokens:** `TOKEN_CLEANUP_CRON` (por defecto diario a las 03:30) y `TOKEN_RETENTION_DAYS` (30).
- **MinIO opcional:** solo si se decide usar S3; el almacenamiento local es el modo por defecto y el recomendado para
  una instancia única.

## Verificación posterior al despliegue

```bash
curl -fsS https://app.ejemplo.com/actuator/health
curl -fsS https://app.ejemplo.com/api/v1/public/config
```

Con seed deshabilitado no hay usuarios demo. El primer arranque crea el usuario `SUPER_ADMIN_PLATFORM` a partir de
`APP_BOOTSTRAP_EMAIL`/`APP_BOOTSTRAP_PASSWORD`; el proceso es idempotente y no modifica una cuenta existente. Después de
verificar el acceso, elimina ambas variables del `.env` y reinicia el backend. Con ese usuario crea la empresa y la
primera copropiedad desde el portal de plataforma.

## Alcance no automatizado

La recepción de correo se verifica en el buzón del destinatario; el envío sale por SMTP. La compilación y firma de APK/IPA,
la publicación en tiendas, las notificaciones push FCM/APNs y el almacenamiento S3 quedan fuera de este compose hasta que
se elijan cuentas, credenciales y proveedor. El detalle y los bloqueos se mantienen en `docs/HANDOFF.md`.
