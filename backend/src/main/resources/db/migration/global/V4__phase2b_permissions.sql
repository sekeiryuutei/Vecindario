INSERT INTO permissions (code, description) VALUES
 ('VISITORS_VIEW_OWN', 'Ver invitaciones y solicitudes de visita de los inmuebles propios'),
 ('VISITORS_RESPOND_OWN', 'Autorizar o rechazar visitantes que llegan a los inmuebles propios'),
 ('PACKAGES_VIEW_OWN', 'Ver los paquetes de los inmuebles propios'),
 ('INCIDENTS_VIEW', 'Ver novedades de portería'),
 ('INCIDENTS_CREATE', 'Registrar novedades de portería'),
 ('INCIDENTS_MANAGE', 'Asignar y cerrar novedades de portería'),
 ('SECURITY_SUMMARY_VIEW', 'Ver el resumen operativo de seguridad');

INSERT INTO role_permissions (role_code, permission_code)
SELECT r, p FROM unnest(ARRAY['PROPIETARIO','ARRENDATARIO','RESIDENTE']) AS r,
                 unnest(ARRAY['VISITORS_VIEW_OWN','VISITORS_RESPOND_OWN','PACKAGES_VIEW_OWN']) AS p;

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'PORTERO', p FROM unnest(ARRAY['INCIDENTS_VIEW','INCIDENTS_CREATE','SECURITY_SUMMARY_VIEW']) AS p;

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'SUPERVISOR_PORTERIA', p FROM unnest(ARRAY['INCIDENTS_VIEW','INCIDENTS_CREATE','INCIDENTS_MANAGE','SECURITY_SUMMARY_VIEW']) AS p;

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'SECRETARIA_ADMINISTRACION', p FROM unnest(ARRAY['INCIDENTS_VIEW','SECURITY_SUMMARY_VIEW']) AS p;
