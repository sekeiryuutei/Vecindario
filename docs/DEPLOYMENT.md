# Despliegue

**Local/servidor único**: `docker compose up -d --build` (ver README). Volúmenes con nombre: `vecindad_postgres_data`,
`vecindad_redis_data`, `vecindad_minio_data` (sobreviven a `docker compose down`; `down -v` los borra).

**Producción (checklist)**
1. `APP_ENV=production`, `APP_SEED_ENABLED=false`, `SWAGGER_ENABLED=false`, `MAIL_DEV_LOG_LINKS=false`.
2. Claves nuevas y largas: `DATABASE_PASSWORD`, `REDIS_PASSWORD`, `MINIO_ROOT_PASSWORD`, `JWT_SECRET`.
3. TLS: pon un proxy con HTTPS (Caddy/Traefik/Nginx con certificados) delante del puerto `NGINX_PORT`.
4. Respaldos programados con `scripts/backup.sh` (cron) y copia fuera del servidor; prueba la restauración.
5. `CORS_ALLOWED_ORIGINS` solo con los orígenes reales.
6. Fija versiones de las imágenes `quay.io/minio/minio` y `quay.io/minio/mc` (hoy `latest`; MinIO ya no publica en Docker Hub).

**Backups**: `pg_dump` incluye todos los schemas (público + cada copropiedad). No hay respaldo en la nube configurado.
