# Arquitectura

## Backend

Monolito modular (no microservicios) con arquitectura hexagonal por módulo:

```
com.codevam.vecindad
├── shared/          kernel: tenancy, errores, seguridad, paginación, utilidades JDBC, correo
├── config/          AppProperties, SecurityConfig, OpenAPI, validación de producción, scheduling
├── bootstrap/       seed demo y creación idempotente del primer superadministrador
├── identity/        usuarios, login, refresh, membresías, roles/permisos, limpieza de tokens
├── tenancy/         empresas administradoras, copropiedades y aprovisionamiento de schemas
├── audit/           auditoría append-only
├── properties/      inmuebles, personas, relaciones y vehículos
├── access/          portería vehicular, visitantes, paquetería y novedades
├── billing/         cartera, pagos, facturación, notas, intereses y libro mayor
├── reconciliation/  conciliación bancaria CSV/XLSX
├── payments/        Wompi, intenciones y webhook
├── certificates/    certificados PDF y verificación pública
├── reservations/    zonas comunes y reservas
├── pqrs/            PQRS, convivencia y SLA
├── communications/  comunicados y acuse de lectura
├── notifications/   preferencias y registro de entregas
├── documents/       repositorio documental con StoragePort
├── assemblies/      asambleas, quórum y votación ponderada
├── suppliers/       proveedores y contratos
├── maintenance/     activos, planes y órdenes de trabajo
├── budget/          presupuestos y ejecuciones
├── reports/         reportes de cartera, presupuesto y mantenimiento
└── camera/          adaptador LPR desacoplado
```

Reglas:

- Los controllers no tienen lógica de negocio; los casos de uso dependen de **puertos** (interfaces) y los adaptadores
  los implementan.
- **JDBC para el schema público y JPA/JDBC por tenant.** Las tablas globales se acceden con nombre calificado
  (`public.tabla`); las operativas usan `TenantJdbc` con el marcador `{s}`, resuelto desde el `TenantContext` validado.
  Así una consulta global no puede caer por accidente en un schema de tenant y viceversa.
- Transacciones con `JpaTransactionManager`; `JdbcTemplate` se une a la transacción JPA. La auditoría usa `REQUIRES_NEW`
  para persistir aunque la operación haga rollback.
- Dinero siempre `BigDecimal`/`NUMERIC(14,2)`; operaciones financieras con bloqueo pesimista e idempotencia.

## Multi-tenancy

1. `POST /auth/login` valida credenciales y devuelve las membresías activas.
2. `POST /auth/select-tenant` verifica la membresía y emite un JWT con `tid` de la copropiedad.
3. `JwtAuthenticationFilter` valida la firma, comprueba usuario/membresía/tenant en base de datos y publica el contexto.
4. `TenantMigrationRunner` aplica Flyway a cada schema al arrancar o al aprovisionar una copropiedad.
5. Cada petición de negocio queda acotada al schema activo por `{s}` y por los puertos JDBC.

## Frontend

```
frontend/src/app
├── core/            auth, api, models, guards, interceptors, branding, pipes, QR, utilidades
├── shared/          componentes de UI reutilizables (header, modal, empty state, paginación)
├── layout/          shell responsive con menús filtrados por permisos
└── pages/
    ├── public/      login, recuperación, selección de copropiedad, verificación y Wompi
    ├── admin/       administración integral de la copropiedad
    ├── porter/      control de acceso, visitantes, paquetería y novedades
    ├── resident/    portal del propietario/arrendatario/residente
    └── platform/    empresas, copropiedades y auditoría global
```

- Angular 20 standalone con signals y `provideAppInitializer` para cargar el branding.
- `ApiService` centraliza las llamadas a `/api/v1`; los interceptores agregan el Bearer, refrescan en 401 y muestran
  errores legibles.
- Los guards validan sesión, tenant activo y permisos. El shell oculta accesos que el rol no puede usar.
- La PWA usa `@angular/service-worker` con precarga de assets y navegación SPA. El mismo build se empaqueta con
  Capacitor para Android/iOS.
- El contenedor `vecindad_frontend` sirve el build con Nginx; el Nginx del borde enruta `/api` al backend y el resto al
  frontend.

## Despliegue

Docker Compose con cinco servicios y una red interna. Solo Nginx se publica en todas las interfaces; PostgreSQL,
Redis, backend y frontend escuchan en `127.0.0.1`. El proxy TLS externo termina HTTPS. Ver
[DEPLOYMENT.md](DEPLOYMENT.md) y [SECURITY.md](SECURITY.md).
