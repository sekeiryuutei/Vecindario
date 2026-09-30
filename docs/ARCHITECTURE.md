# Arquitectura

**Monolito modular** (no microservicios) con arquitectura hexagonal por módulo:

```
com.codevam.vecindad
├── shared/        kernel: tenancy, errores, seguridad, paginación, utilidades JDBC
├── config/        AppProperties, SecurityConfig, JPA multi-tenant, OpenAPI
├── identity/      usuarios, login, refresh, membresías, roles/permisos
│   ├── domain/            registros e enums puros
│   ├── application/       casos de uso (AuthService, MemberService, RoleService) + application/port/out
│   └── adapter/in/web     controllers + DTOs · adapter/out/{persistence,security,ratelimit,mail}
├── tenancy/       empresas administradoras, copropiedades, aprovisionamiento de schemas
├── audit/         auditoría append-only
├── properties/    inmuebles (primer módulo con datos POR TENANT, vía JPA)
└── bootstrap/     seed y configuración pública
```

Reglas: los controllers no tienen lógica de negocio; los casos de uso dependen de **puertos** (interfaces) y los adaptadores
los implementan (JDBC para tablas globales, JPA/Hibernate para tablas de tenant). Los bounded contexts futuros
(vehículos, finanzas, PQRS…) se agregan como paquetes hermanos con el mismo esquema.

**Decisión: JDBC para el schema público y JPA para el de tenant.** Las tablas globales se acceden siempre con nombre
calificado (`public.tabla`); las de tenant se resuelven por `search_path`. Así una consulta global nunca puede caer por
accidente en un schema de tenant y viceversa.

Transacciones: `JpaTransactionManager` único. El `JdbcTemplate` se une a la transacción JPA en las mismas conexiones.
La auditoría usa `REQUIRES_NEW` para persistir aunque la operación haga rollback.
