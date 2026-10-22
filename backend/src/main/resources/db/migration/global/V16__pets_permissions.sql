-- Migración global V16: permisos del registro de mascotas.

INSERT INTO permissions (code, description) VALUES
 ('PETS_VIEW', 'Ver las mascotas registradas en la copropiedad'),
 ('PETS_CREATE', 'Registrar mascotas'),
 ('PETS_UPDATE', 'Editar mascotas'),
 ('PETS_DELETE', 'Eliminar mascotas'),
 ('PETS_VIEW_OWN', 'Ver las mascotas de los inmuebles propios'),
 ('PETS_MANAGE_OWN', 'Registrar, modificar y eliminar mascotas de los inmuebles propios')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
SELECT r, p FROM unnest(ARRAY['PROPIETARIO','ARRENDATARIO','RESIDENTE']) AS r,
                 unnest(ARRAY['PETS_VIEW_OWN','PETS_MANAGE_OWN']) AS p
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
SELECT r, 'PETS_VIEW' FROM unnest(ARRAY['PORTERO','SUPERVISOR_PORTERIA','CONSEJO']) AS r
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'SECRETARIA_ADMINISTRACION', p
FROM unnest(ARRAY['PETS_VIEW','PETS_CREATE','PETS_UPDATE','PETS_DELETE']) AS p
ON CONFLICT DO NOTHING;
