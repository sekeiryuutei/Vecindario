# VECINDAD — plataforma de administración de copropiedades

Desarrollada por **CodeVam**. Nombre y empresa visibles configurables (`APP_NAME`, `APP_COMPANY` en `.env`).

> **Estado: Fase 1 + Fase 2a de 5 (backend + infraestructura).**
> Este ZIP incluye la base de la plataforma: multi-tenancy (un schema PostgreSQL por copropiedad), Flyway, JWT con refresh
> rotativo, RBAC con permisos configurables por copropiedad, auditoría, gestión de usuarios/membresías con historial,
> área de super admin, inmuebles (primer módulo de tenant), Swagger, seed, Docker, Nginx, PostgreSQL, Redis y MinIO.
> La **Fase 2a** agrega: personas, relaciones propietario/arrendatario/residente con inmuebles (N:N), tipos de vehículo y límites
> configurables, vehículos con la regla "vehículo dentro", control de acceso vehicular de portería (entrada/salida atómicas),
> alertas de seguridad y la vista del residente limitada a sus propios inmuebles.
> **No incluye todavía**: visitantes/QR, paquetes y novedades (Fase 2b), finanzas, PQRS, reservas, etc. (Fases 3 a 5) ni el frontend.
> Ver [docs/ROADMAP.md](docs/ROADMAP.md).
>
> **Importante:** este código fue escrito sin poder compilarlo ni ejecutarlo en el entorno donde se generó (sin Maven,
> Docker ni red). Es posible que el primer `docker compose up --build` muestre errores de compilación menores. Pega la
> salida y se corrigen. Los tests existen pero **no se han corrido**.

## Requisitos

- Docker 24+ con Docker Compose v2 (`docker compose version`)
- Para correr tests fuera de Docker: JDK 21 y Maven 3.9+ (los `*IT` además requieren Docker)

## Instalación paso a paso

**PASO 1 y 2.** Descarga y descomprime `vecindad-backend.zip`. Debe quedar la carpeta `vecindad/`:

```bash
unzip vecindad-backend.zip
cd vecindad
```

**PASO 3 y 4 (cuando exista la Fase 5).** Descarga `vecindad-frontend.zip` y descomprímelo **dentro** de `vecindad/`
para obtener `vecindad/frontend/` (hermano de `backend/`). Mientras tanto no es necesario.

**PASO 5.** Crea tu `.env`:

```bash
cp .env.example .env
```

**PASO 6.** Edita `.env`. Como mínimo cambia `DATABASE_PASSWORD`, `REDIS_PASSWORD`, `MINIO_ROOT_PASSWORD` y `JWT_SECRET`
(`openssl rand -base64 48`). Si algún puerto está ocupado, cámbialo (`NGINX_PORT`, `BACKEND_PORT`, `POSTGRES_PORT`, …).

**PASO 7.** Levanta todo:

```bash
docker compose up -d --build
docker compose ps                       # postgresql, redis, backend y nginx "healthy"
docker compose logs -f vecindad_backend # espera "Started VecindadApplication"
```

MinIO es opcional (`--profile storage`) y hoy puede no descargarse (MinIO retiró sus imágenes públicas); no se usa hasta la fase de documentos.
Con frontend (Fase 5): `docker compose --profile frontend up -d --build`.

**PASO 8. Migraciones y seed: son automáticas.** Al arrancar el backend:
1. Flyway migra el schema `public` (`db/migration/global`).
2. Migra el schema de **cada** copropiedad existente (`db/migration/tenant`).
3. Si `APP_SEED_ENABLED=true` (y `APP_ENV` no es `production`) carga los datos demo, una sola vez.

**PASO 9. Accede** (con los puertos de ejemplo):

| Qué | URL |
|---|---|
| API vía Nginx | http://localhost:48124/api/v1 |
| Swagger UI | http://localhost:48124/swagger-ui.html |
| Health | http://localhost:48124/actuator/health |

Prueba rápida automática: en **PowerShell** `.\scripts\smoke-test.ps1` (puede requerir `Set-ExecutionPolicy -Scope Process Bypass`); en Git Bash/WSL `./scripts/smoke-test.sh` (solo cubre la Fase 1).

## Usuarios demo (solo desarrollo; contraseña = `APP_SEED_PASSWORD`, por defecto `Demo#2026!`)

| Correo | Rol | Copropiedades |
|---|---|---|
| superadmin@vecindad.local | SUPER_ADMIN_PLATFORM | (área /api/v1/platform) |
| admin@vecindad.local | ADMINISTRADOR | Demo Norte y Demo Sur (debe elegir con `select-tenant`) |
| admin.sur@demo-sur.local | ADMINISTRADOR | Demo Sur |
| secretaria@demo-norte.local | SECRETARIA_ADMINISTRACION | Norte |
| contador@demo-norte.local | CONTADOR | Norte |
| consejo@demo-norte.local | CONSEJO | Norte |
| portero@demo-norte.local | PORTERO | Norte |
| propietario@demo-norte.local | PROPIETARIO | Norte **y** Sur (misma persona en dos tenants) |

Datos demo de la Fase 2a en *Conjunto Demo Norte*: Juan Pérez es propietario de 3 inmuebles (T1-101, T1-102, T2-101) y además de A-101 en *Demo Sur* (misma persona, mismo usuario); María Gómez es arrendataria de T1-101. Placas de prueba: `ABC123` (carro), `GHI789` (segundo carro de T1-101, permitido por una excepción de límite), `XYZ98A` (moto), `DEF456`.

Empresa: *CodeVam Administración Demo*. Copropiedades: *Conjunto Demo Norte* y *Conjunto Demo Sur*.

### Portería (ejemplo)

```bash
# con el token del portero (portero@demo-norte.local)
curl -s "$BASE/access/vehicles/lookup?plate=ABC123" -H "Authorization: Bearer <token>"
curl -s -X POST $BASE/access/entry -H "Authorization: Bearer <token>" -H 'Content-Type: application/json' -d '{"plate":"ABC123"}'
# ahora, modificar ese vehículo (admin) devuelve 409 VEHICLE_INSIDE hasta registrar la salida:
curl -s -X POST $BASE/access/exit  -H "Authorization: Bearer <token>" -H 'Content-Type: application/json' -d '{"plate":"ABC123"}'
```

### Flujo con curl

```bash
BASE=http://localhost:48124/api/v1
# 1) login (admin tiene 2 copropiedades → aún sin tenant activo)
curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"admin@vecindad.local","password":"Demo#2026!"}'
# 2) elegir copropiedad (el backend valida la membresía)
curl -s -X POST $BASE/auth/select-tenant -H "Authorization: Bearer <accessToken>" \
  -H 'Content-Type: application/json' -d '{"tenantId":"<id de la copropiedad>"}'
# 3) usar el nuevo accessToken
curl -s $BASE/properties -H "Authorization: Bearer <nuevo accessToken>"
```

## Tests

```bash
cd backend
mvn test      # unitarios (no requieren Docker)
mvn verify    # unitarios + integración (*IT) con PostgreSQL real vía Testcontainers → requiere Docker corriendo
```
Sin Maven local (solo unitarios):
`docker run --rm -v "$PWD/backend":/app -w /app maven:3.9-eclipse-temurin-21 mvn -B test`

Los `*IT` cubren aislamiento entre copropiedades (IDs ajenos, `tenantId`/headers manipulados, tokens falsificados, membresía
revocada, tenant suspendido), login/lockout, rotación y reutilización de refresh tokens, invitación/activación, recuperación
de contraseña y permisos por copropiedad. Ver [docs/TESTING.md](docs/TESTING.md).

## Respaldo y restauración

```bash
./scripts/backup.sh                                  # backups/vecindad_<fecha>.sql.gz (todos los schemas)
./scripts/restore.sh backups/vecindad_<fecha>.sql.gz # DESTRUYE la base actual
```

## Troubleshooting

| Síntoma | Qué hacer |
|---|---|
| **Puerto ocupado** (`port is already allocated`) | Cambia el puerto en `.env` y `docker compose up -d` de nuevo. |
| **PostgreSQL no inicia** | `docker compose logs vecindad_postgresql`. Si cambiaste `DATABASE_PASSWORD` después del primer arranque, el volumen conserva la clave vieja: `docker compose down -v` (borra datos) o revierte la clave. |
| **Redis no inicia** | `docker compose logs vecindad_redis`. Verifica `REDIS_PASSWORD`. Si Redis cae, el login sigue funcionando (el rate limit falla abierto; el bloqueo por cuenta en BD sigue activo). |
| **Falla una migración** | `docker compose logs vecindad_backend`. Flyway aborta el arranque a propósito. Corrige y reinicia; no edites migraciones ya aplicadas (crea una nueva `V<n>__...sql`). |
| **"tenant schema no existe"** | Reinicia el backend: `TenantMigrationRunner` recrea/migra los schemas de copropiedades ACTIVAS/SUSPENDIDAS. Si la copropiedad quedó en `FAILED`, revisa los logs y vuelve a crearla. |
| **S3/MinIO falla** | `docker compose logs vecindad_minio vecindad_minio_init`. El bucket lo crea `vecindad_minio_init`. (El uso de S3 llega en la fase de documentos.) |
| **Wompi no responde** | Llega en la Fase 3; requiere llaves reales en `.env`. |
| **Frontend no conecta con el backend** | Abre `http://localhost:<NGINX_PORT>/api/v1/public/config`. Si responde, revisa `CORS_ALLOWED_ORIGINS`. Si no, mira `docker compose ps` y los logs del backend. |
| `JWT_SECRET es obligatorio…` | Define un `JWT_SECRET` de 32+ caracteres en `.env`. |
| Backend "unhealthy" al inicio | Normal hasta ~60 s mientras migra; revisa `docker compose logs -f vecindad_backend`. |

## Estructura

```
vecindad/
├── backend/            Spring Boot 3 (Java 21), Maven, arquitectura hexagonal por módulo
├── frontend/           (Fase 5) Angular + Ionic + Capacitor + PWA
├── docker/             nginx.conf y Dockerfile del frontend
├── scripts/            backup.sh, restore.sh, smoke-test.sh
├── docs/               ARCHITECTURE, MULTI_TENANCY, SECURITY, DATABASE, API, DEPLOYMENT, TESTING, ROADMAP
├── docker-compose.yml
├── .env.example
└── README.md
```

## Git

```bash
git init && git add . && git commit -m "Initial Vecindad platform"
```
`.env`, `target/`, `node_modules/`, `backups/` están en `.gitignore`.
