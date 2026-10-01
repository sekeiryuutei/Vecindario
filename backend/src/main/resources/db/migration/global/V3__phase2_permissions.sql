INSERT INTO permissions (code, description) VALUES
 ('PARKING_MANAGE', 'Administrar espacios de parqueadero'),
 ('VEHICLE_RULES_MANAGE', 'Configurar tipos de vehículo y límites por inmueble'),
 ('SECURITY_ALERTS_VIEW', 'Ver y resolver alertas de seguridad'),
 ('UNITS_VIEW_OWN', 'Ver los inmuebles propios (relacionados con la persona)'),
 ('VEHICLES_VIEW_OWN', 'Ver los vehículos de los inmuebles propios'),
 ('VEHICLES_MANAGE_OWN', 'Registrar, modificar y eliminar vehículos de los inmuebles propios');

INSERT INTO role_permissions (role_code, permission_code)
SELECT r, p FROM unnest(ARRAY['PROPIETARIO','ARRENDATARIO','RESIDENTE']) AS r,
                 unnest(ARRAY['UNITS_VIEW_OWN','VEHICLES_VIEW_OWN','VEHICLES_MANAGE_OWN']) AS p;

INSERT INTO role_permissions (role_code, permission_code)
SELECT r, 'SECURITY_ALERTS_VIEW' FROM unnest(ARRAY['PORTERO','SUPERVISOR_PORTERIA']) AS r;

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'SECRETARIA_ADMINISTRACION', p FROM unnest(ARRAY['VEHICLES_CREATE','VEHICLES_UPDATE','PARKING_MANAGE']) AS p;
