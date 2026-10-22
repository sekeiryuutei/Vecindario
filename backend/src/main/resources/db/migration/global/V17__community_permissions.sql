-- Migración global V17: permisos de junta directiva, citas, capacitaciones,
-- personal operativo y expediente del inmueble.

INSERT INTO permissions (code, description) VALUES
 ('BOARD_VIEW', 'Ver la junta directiva de la copropiedad'),
 ('BOARD_MANAGE', 'Administrar los miembros de la junta directiva'),
 ('APPOINTMENTS_VIEW', 'Ver todas las citas con la administración'),
 ('APPOINTMENTS_MANAGE', 'Confirmar, reprogramar y cerrar citas'),
 ('APPOINTMENTS_VIEW_OWN', 'Ver las citas propias'),
 ('APPOINTMENTS_REQUEST', 'Solicitar y cancelar citas propias'),
 ('TRAININGS_VIEW', 'Ver las capacitaciones de la copropiedad'),
 ('TRAININGS_MANAGE', 'Crear y administrar capacitaciones e inscripciones'),
 ('TRAININGS_VIEW_OWN', 'Ver las capacitaciones disponibles para el residente'),
 ('TRAININGS_ENROLL_OWN', 'Inscribirse y cancelar la inscripción a capacitaciones'),
 ('STAFF_VIEW', 'Ver el directorio del personal operativo'),
 ('STAFF_MANAGE', 'Administrar el directorio del personal operativo'),
 ('UNIT_FILE_VIEW', 'Ver el expediente de cualquier inmueble'),
 ('UNIT_FILE_MANAGE', 'Administrar notas y documentos del expediente del inmueble'),
 ('UNIT_FILE_VIEW_OWN', 'Ver el expediente de los inmuebles propios')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code) VALUES
 ('SECRETARIA_ADMINISTRACION', 'BOARD_MANAGE'),
 ('SECRETARIA_ADMINISTRACION', 'APPOINTMENTS_VIEW'),
 ('SECRETARIA_ADMINISTRACION', 'APPOINTMENTS_MANAGE'),
 ('SECRETARIA_ADMINISTRACION', 'TRAININGS_MANAGE'),
 ('SECRETARIA_ADMINISTRACION', 'STAFF_MANAGE'),
 ('SECRETARIA_ADMINISTRACION', 'UNIT_FILE_MANAGE'),
 ('CONSEJO', 'APPOINTMENTS_VIEW'),
 ('CONSEJO', 'UNIT_FILE_VIEW'),
 ('CONTADOR', 'UNIT_FILE_VIEW')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
SELECT r, 'BOARD_VIEW' FROM unnest(ARRAY[
    'SECRETARIA_ADMINISTRACION','CONTADOR','CONSEJO','PROPIETARIO','ARRENDATARIO','RESIDENTE',
    'PORTERO','SUPERVISOR_PORTERIA','MANTENIMIENTO','PROVEEDOR']) AS r
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
SELECT r, 'TRAININGS_VIEW' FROM unnest(ARRAY[
    'SECRETARIA_ADMINISTRACION','CONTADOR','CONSEJO','PROPIETARIO','ARRENDATARIO','RESIDENTE',
    'PORTERO','SUPERVISOR_PORTERIA','MANTENIMIENTO','PROVEEDOR']) AS r
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
SELECT r, 'STAFF_VIEW' FROM unnest(ARRAY[
    'SECRETARIA_ADMINISTRACION','CONSEJO','PORTERO','SUPERVISOR_PORTERIA','MANTENIMIENTO']) AS r
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
SELECT r, p FROM unnest(ARRAY['PROPIETARIO','ARRENDATARIO','RESIDENTE']) AS r,
                 unnest(ARRAY['APPOINTMENTS_VIEW_OWN','APPOINTMENTS_REQUEST',
                              'TRAININGS_VIEW_OWN','TRAININGS_ENROLL_OWN',
                              'UNIT_FILE_VIEW_OWN']) AS p
ON CONFLICT DO NOTHING;
