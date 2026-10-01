# Pruebas

- `mvn test` → unitarios: `SchemaNames` (inyección), `TenantContext`, `JwtService` (firma, expiración, llave ajena,
  secreto corto/dev en producción), política de contraseñas y tokens.
- `mvn verify` → además `*IT` con PostgreSQL 16 real (Testcontainers; requiere Docker):
  - `TenantIsolationIT`: filas solo en su schema; ignorar `tenantId`/`X-Tenant-Id`; GET por ID de otro tenant = 404;
    `select-tenant` ajeno = 403; token válido con `tid` sin membresía = 403; JWT falsificado = 401; membresía revocada y
    tenant suspendido surten efecto inmediato; residente sin acceso a inmuebles; auditoría acotada al tenant;
    administrador de tenant no entra al área de plataforma.
  - `AuthFlowIT`: error genérico, bloqueo tras 5 fallos, rotación y reutilización de refresh, logout, cambio de
    contraseña, recuperación de un solo uso, invitación/activación, roles inválidos, no auto-modificarse, permisos
    ajustados por copropiedad sin afectar a otra.

  - `VehicleAccessIT` (Fase 2a): vehículo dentro no se modifica ni elimina (admin ni residente) hasta registrar la salida; entrada duplicada,
    vehículo bloqueado, placa desconocida y salida sin entrada se rechazan y generan alertas; **6 entradas simultáneas → exactamente 1**;
    límites por tipo y por inmueble; placa única; el residente solo ve/toca lo suyo (404 en lo ajeno); el portero no ve personas;
    personas, vehículos y eventos de un tenant son invisibles desde otro.

Pendiente en fases siguientes: visitantes, reservas, pagos, conciliación y E2E Playwright.
