# Seguridad

## Autenticación y sesiones

- **Contraseñas**: BCrypt (costo 12; 4 en tests). Política: 10–72 caracteres, letras y números.
- **Login**: mensaje genérico (no revela si el correo existe), comparación de hash ficticio para igualar tiempos,
  bloqueo de cuenta tras 5 fallos (15 min) y rate limit por IP+correo en Redis (falla abierto si Redis cae).
- **Tokens**: access JWT HS256 de 15 min (solo `sub`, `tid`, `jti`). Refresh opaco de 256 bits, guardado como SHA-256,
  **rotativo**: cada refresh revoca el anterior; reutilizar uno ya rotado revoca todas las sesiones del usuario.
- **Revocación inmediata**: usuario desactivado, membresía suspendida/revocada o copropiedad suspendida dejan de funcionar
  en la siguiente petición (estado y permisos se leen de BD, no del token).
- **Purga**: job programado que elimina tokens caducados/revocados según `TOKEN_RETENTION_DAYS`.

## Autorización y aislamiento

- Rutas `/api/v1/platform/**` exigen `SUPER_ADMIN_PLATFORM`; el resto usa
  `@PreAuthorize("hasAuthority('PERMISO')")`. SUPER_ADMIN **no** recibe permisos de copropiedad.
- El `tenantId` sale exclusivamente del JWT validado y la membresía se comprueba en cada petición; nunca se confía en
  parámetros o cabeceras del cliente.
- **Permisos configurables**: base global por rol + ajustes por copropiedad (`PUT /api/v1/roles/{rol}/permissions/{perm}`).
  ADMINISTRADOR siempre conserva todos. Solo un ADMINISTRADOR puede asignar/modificar a otro ADMINISTRADOR; nadie puede
  modificar su propio acceso.
- **Auditoría**: `audit_logs` append-only (trigger impide UPDATE/DELETE). Claves con pass/secret/token/authorization se
  descartan antes de guardar.
- **Errores**: formato `{code, message, timestamp, path, traceId, details}`; sin stack traces al cliente.

## Integraciones y correo

- **SMTP real**: invitaciones, recuperación de contraseña y notificaciones usan `SmtpMailGateway`. En desarrollo,
  si no hay SMTP y `MAIL_DEV_LOG_LINKS=true`, el enlace se imprime en el log. En producción el arranque exige
  `SMTP_HOST` y `MAIL_FROM`, y `MAIL_DEV_LOG_LINKS` debe ser `false`.
- **Entregas**: una notificación solo se marca `SENT` cuando el transporte confirmó el envío; sin SMTP queda `FAILED`
  o `SKIPPED`, nunca `SENT`.
- **Wompi**: la firma del webhook es obligatoria en producción y se compara en tiempo constante. Se validan monto,
  moneda, referencia y estado antes de aplicar el pago; el procesamiento usa bloqueo pesimista e idempotencia. Con
  `WOMPI_ENABLED=false` o llaves incompletas, el checkout falla cerrado.
- **Certificados**: PDF con código de verificación; la ruta pública solo devuelve los datos necesarios para validar el
  documento.
- **Documentos**: almacenamiento local persistente con permisos por rol y descarga autenticada; en producción
  `STORAGE_LOCAL_DIR` debe ser absoluto y fuera de `/tmp`.

## Credenciales por copropiedad

Las llaves de Wompi y las claves del proveedor de IA pueden definirse por copropiedad desde el área de plataforma. Se
guardan cifradas con AES-GCM en `public.tenant_integrations`, con la llave maestra fuera de la base de datos
(`INTEGRATIONS_ENCRYPTION_KEY`, mínimo 32 bytes, obligatorio en producción). El cifrado autentica el contenido: una
alteración manual en la tabla se detecta al descifrar y la API reporta un error en lugar de usar una credencial corrupta.

La API nunca devuelve llaves privadas ni secretos: el panel solo muestra el estado (configurada o heredada) y la llave
pública de Wompi, que es pública por diseño porque viaja en la URL de checkout. La rotación conserva los secretos que no
se reenvían y el borrado de la configuración propia devuelve la copropiedad a los valores globales del entorno.
Cambiar `INTEGRATIONS_ENCRYPTION_KEY` exige volver a cargar todas las credenciales por copropiedad.

## Infraestructura y cabeceras

- Nginx aplica CSP, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy`,
  `Permissions-Policy` (cámara solo para el escáner QR) y HSTS cuando el proxy TLS envía `X-Forwarded-Proto: https`.
- Límites de tasa: autenticación `60r/m` con burst 20 en el borde; el backend limita el inicio de sesión a `10` intentos
  por minuto y por IP+correo, con bloqueo temporal de la cuenta tras `5` intentos fallidos; API general `30r/s` con burst 60;
  conexiones por IP limitadas.
- PostgreSQL, Redis, backend y frontend se publican solo en `127.0.0.1`; únicamente Nginx escucha en todas las interfaces.
- Contenedores con `no-new-privileges`; el backend corre como usuario sin privilegios, con filesystem de solo lectura y
  `/tmp` efímero.
- Rotación de logs `json-file` (10 MB × 3) y `server_tokens off`.

## Producción

`APP_ENV=production` rechaza secretos de desarrollo, seed activo, Swagger público y CORS inseguro. Desactiva Swagger
(`SWAGGER_ENABLED=false`), usa HTTPS delante de Nginx y cambia todas las claves de `.env`. Los orígenes nativos exactos
`https://localhost` (Android) y `capacitor://localhost` (iOS) están permitidos para la app Capacitor; cualquier origen
HTTP o localhost con puerto se rechaza.

En una instalación sin seed, `APP_BOOTSTRAP_EMAIL`/`APP_BOOTSTRAP_PASSWORD` crean el primer usuario
`SUPER_ADMIN_PLATFORM` durante el arranque. El runner es idempotente, no eleva cuentas existentes y exige una contraseña
de 12+ caracteres. Retira ambas variables después de verificar el acceso.

## Riesgos conocidos / pendientes

- No hay 2FA ni política de rotación automática de `JWT_SECRET` (cambiarlo invalida todos los access tokens).
- El TLS se termina en un proxy externo; el Nginx del compose escucha HTTP en la red interna.
- El almacenamiento local no está replicado: respalda el volumen `vecindad_storage` junto con PostgreSQL.
- La cola de importaciones de conciliación es interna al proceso; con varias réplicas debe moverse a Redis.
- `npm audit --omit=dev` no reporta vulnerabilidades de runtime. Los avisos restantes pertenecen al toolchain de build
  Angular 20 (servidor de desarrollo, empaquetado) y no se incluyen en el bundle; su corrección requiere una actualización
  mayor a Angular 21.
