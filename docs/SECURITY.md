# Seguridad

- **Contraseñas**: BCrypt (costo 12; 4 en tests). Política: 10–72 caracteres, letras y números.
- **Login**: mensaje genérico (no revela si el correo existe), comparación de hash ficticio para igualar tiempos,
  bloqueo de cuenta tras 5 fallos (15 min) y rate limit por IP+correo en Redis (falla abierto si Redis cae).
- **Tokens**: access JWT HS256 de 15 min (solo `sub`, `tid`, `jti`). Refresh opaco de 256 bits, guardado como SHA-256,
  **rotativo**: cada refresh revoca el anterior; reutilizar uno ya rotado revoca todas las sesiones del usuario.
- **Revocación inmediata**: usuario desactivado, membresía suspendida/revocada o copropiedad suspendida dejan de funcionar
  en la siguiente petición (estado y permisos se leen de BD, no del token).
- **Autorización**: rutas `/api/v1/platform/**` exigen `SUPER_ADMIN_PLATFORM`; el resto usa
  `@PreAuthorize("hasAuthority('PERMISO')")`. SUPER_ADMIN **no** recibe permisos de copropiedad.
- **Permisos configurables**: base global por rol + ajustes por copropiedad (`PUT /api/v1/roles/{rol}/permissions/{perm}`).
  ADMINISTRADOR siempre conserva todos. Solo un ADMINISTRADOR puede asignar/modificar a otro ADMINISTRADOR; nadie puede
  modificar su propio acceso.
- **Auditoría**: `audit_logs` append-only (trigger impide UPDATE/DELETE). Claves con pass/secret/token/authorization se
  descartan antes de guardar.
- **Errores**: formato `{code, message, timestamp, path, traceId, details}`; sin stack traces al cliente.
- **Producción**: `APP_ENV=production` rechaza el `JWT_SECRET` de desarrollo y omite el seed. Desactiva Swagger
  (`SWAGGER_ENABLED=false`), usa HTTPS delante de Nginx y cambia todas las claves de `.env`.
- Postgres, Redis, MinIO y el backend se publican solo en `127.0.0.1`; únicamente Nginx escucha en todas las interfaces.

## Riesgos conocidos / pendientes
- `MAIL_DEV_LOG_LINKS=true` imprime tokens de activación en el log: solo desarrollo.
- Aún no hay 2FA, ni política de rotación de `JWT_SECRET` (cambiarlo invalida todos los access tokens).
- Nginx del compose escucha en HTTP; el TLS se configura en el despliegue (ver DEPLOYMENT.md).
