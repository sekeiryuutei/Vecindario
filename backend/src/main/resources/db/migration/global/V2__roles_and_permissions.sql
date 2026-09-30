INSERT INTO roles (code, name, scope) VALUES
 ('SUPER_ADMIN_PLATFORM',     'Super administrador de plataforma', 'PLATFORM'),
 ('ADMINISTRADOR',            'Administrador',                     'TENANT'),
 ('SECRETARIA_ADMINISTRACION','Secretaría de administración',      'TENANT'),
 ('CONTADOR',                 'Contador',                          'TENANT'),
 ('CONSEJO',                  'Consejo de administración',         'TENANT'),
 ('PROPIETARIO',              'Propietario',                       'TENANT'),
 ('ARRENDATARIO',             'Arrendatario',                      'TENANT'),
 ('RESIDENTE',                'Residente',                         'TENANT'),
 ('PORTERO',                  'Portero',                           'TENANT'),
 ('SUPERVISOR_PORTERIA',      'Supervisor de portería',            'TENANT'),
 ('MANTENIMIENTO',            'Personal de mantenimiento',         'TENANT'),
 ('PROVEEDOR',                'Proveedor',                         'TENANT');

INSERT INTO permissions (code, description) VALUES
 ('USERS_VIEW', 'Ver usuarios de la copropiedad'),
 ('USERS_MANAGE', 'Invitar, cambiar rol, suspender y revocar usuarios'),
 ('ROLES_MANAGE', 'Configurar permisos por rol'),
 ('TENANT_SETTINGS_VIEW', 'Ver configuración de la copropiedad'),
 ('TENANT_SETTINGS_MANAGE', 'Modificar configuración de la copropiedad'),
 ('AUDIT_VIEW', 'Consultar auditoría'),
 ('PROPERTIES_VIEW', 'Ver inmuebles'),
 ('PROPERTIES_CREATE', 'Crear inmuebles'),
 ('PROPERTIES_UPDATE', 'Modificar inmuebles'),
 ('PROPERTIES_DELETE', 'Eliminar inmuebles'),
 ('RESIDENTES_VIEW', 'Ver residentes'),
 ('RESIDENTES_CREATE', 'Crear residentes'),
 ('RESIDENTES_UPDATE', 'Modificar residentes'),
 ('RESIDENTES_DELETE', 'Eliminar residentes'),
 ('VEHICLES_VIEW', 'Ver vehículos'),
 ('VEHICLES_CREATE', 'Crear vehículos'),
 ('VEHICLES_UPDATE', 'Modificar vehículos'),
 ('VEHICLES_DELETE', 'Eliminar vehículos'),
 ('VEHICLES_REGISTER_ENTRY', 'Registrar entrada de vehículos'),
 ('VEHICLES_REGISTER_EXIT', 'Registrar salida de vehículos'),
 ('VISITORS_VIEW', 'Ver visitantes'),
 ('VISITORS_CREATE', 'Crear invitaciones de visitantes'),
 ('VISITORS_AUTHORIZE', 'Autorizar/validar visitantes en portería'),
 ('PACKAGES_VIEW', 'Ver paquetes'),
 ('PACKAGES_MANAGE', 'Registrar y entregar paquetes'),
 ('FINANCE_VIEW', 'Ver cartera y finanzas'),
 ('FINANCE_MANAGE', 'Administrar cartera y finanzas'),
 ('PAYMENTS_CREATE', 'Registrar pagos'),
 ('PAYMENTS_RECONCILE', 'Conciliar pagos'),
 ('PQR_CREATE', 'Crear PQRS'),
 ('PQR_VIEW', 'Ver PQRS'),
 ('PQR_ASSIGN', 'Asignar PQRS'),
 ('PQR_RESPOND', 'Responder PQRS'),
 ('PQR_CLOSE', 'Cerrar PQRS'),
 ('RESERVATIONS_VIEW', 'Ver reservas'),
 ('RESERVATIONS_CREATE', 'Crear reservas'),
 ('RESERVATIONS_APPROVE', 'Aprobar reservas'),
 ('MAINTENANCE_VIEW', 'Ver mantenimientos'),
 ('MAINTENANCE_MANAGE', 'Administrar mantenimientos'),
 ('DOCUMENTS_VIEW', 'Ver documentos'),
 ('DOCUMENTS_MANAGE', 'Administrar documentos'),
 ('COMMUNICATIONS_MANAGE', 'Publicar comunicados'),
 ('REPORTS_VIEW', 'Ver reportes');

-- ADMINISTRADOR recibe todos los permisos por código (ver JdbcRolePermissionAdapter), no por filas.
INSERT INTO role_permissions (role_code, permission_code)
SELECT 'SECRETARIA_ADMINISTRACION', p FROM unnest(ARRAY[
 'USERS_VIEW','PROPERTIES_VIEW','PROPERTIES_CREATE','PROPERTIES_UPDATE','RESIDENTES_VIEW','RESIDENTES_CREATE',
 'RESIDENTES_UPDATE','VEHICLES_VIEW','VISITORS_VIEW','PACKAGES_VIEW','PACKAGES_MANAGE','PQR_VIEW','PQR_ASSIGN',
 'PQR_RESPOND','PQR_CLOSE','RESERVATIONS_VIEW','RESERVATIONS_APPROVE','DOCUMENTS_VIEW','DOCUMENTS_MANAGE',
 'COMMUNICATIONS_MANAGE']) AS p;

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'CONTADOR', p FROM unnest(ARRAY[
 'PROPERTIES_VIEW','FINANCE_VIEW','FINANCE_MANAGE','PAYMENTS_CREATE','PAYMENTS_RECONCILE','REPORTS_VIEW',
 'DOCUMENTS_VIEW']) AS p;

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'CONSEJO', p FROM unnest(ARRAY['FINANCE_VIEW','REPORTS_VIEW','DOCUMENTS_VIEW']) AS p;

INSERT INTO role_permissions (role_code, permission_code)
SELECT r, p FROM unnest(ARRAY['PROPIETARIO','ARRENDATARIO','RESIDENTE']) AS r,
                 unnest(ARRAY['PQR_CREATE','PQR_VIEW','RESERVATIONS_CREATE','RESERVATIONS_VIEW','VISITORS_CREATE']) AS p;

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'PORTERO', p FROM unnest(ARRAY[
 'VEHICLES_VIEW','VEHICLES_REGISTER_ENTRY','VEHICLES_REGISTER_EXIT','VISITORS_VIEW','VISITORS_AUTHORIZE',
 'PACKAGES_VIEW','PACKAGES_MANAGE']) AS p;

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'SUPERVISOR_PORTERIA', p FROM unnest(ARRAY[
 'VEHICLES_VIEW','VEHICLES_REGISTER_ENTRY','VEHICLES_REGISTER_EXIT','VISITORS_VIEW','VISITORS_AUTHORIZE',
 'PACKAGES_VIEW','PACKAGES_MANAGE','REPORTS_VIEW']) AS p;

INSERT INTO role_permissions (role_code, permission_code)
SELECT r, p FROM unnest(ARRAY['MANTENIMIENTO','PROVEEDOR']) AS r,
                 unnest(ARRAY['MAINTENANCE_VIEW']) AS p;
