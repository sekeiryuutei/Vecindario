-- Migración global V7: nuevos permisos de Fase 4 (Zonas comunes, Reservas, Comunicados, Asambleas, Notificaciones)

INSERT INTO permissions (code, description) VALUES
    ('COMMON_AREAS_MANAGE', 'Configurar zonas comunes y horarios'),
    ('RESERVATIONS_VIEW_OWN', 'Ver las reservas de los inmuebles propios'),
    ('RESERVATIONS_MANAGE_OWN', 'Crear y cancelar reservas de los inmuebles propios'),
    ('COMMUNICATIONS_VIEW', 'Ver comunicados y avisos de la copropiedad'),
    ('PQR_VIEW_OWN', 'Ver y gestionar el seguimiento a las PQRS propias'),
    ('ASSEMBLIES_VIEW', 'Ver asambleas programadas y resultados'),
    ('ASSEMBLIES_MANAGE', 'Crear, aperturar y gestionar asambleas, temas y quórum'),
    ('ASSEMBLIES_VOTE', 'Emitir voto en asamblea en representación del inmueble'),
    ('NOTIFICATIONS_MANAGE', 'Configurar canales y disparar notificaciones masivas de cartera/portería');

-- Asignación de permisos por rol
-- Administrador y Secretaría
INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('ADMINISTRADOR', 'COMMON_AREAS_MANAGE'),
    ('ADMINISTRADOR', 'COMMUNICATIONS_VIEW'),
    ('ADMINISTRADOR', 'ASSEMBLIES_VIEW'),
    ('ADMINISTRADOR', 'ASSEMBLIES_MANAGE'),
    ('ADMINISTRADOR', 'NOTIFICATIONS_MANAGE'),
    ('SECRETARIA_ADMINISTRACION', 'COMMON_AREAS_MANAGE'),
    ('SECRETARIA_ADMINISTRACION', 'COMMUNICATIONS_VIEW'),
    ('SECRETARIA_ADMINISTRACION', 'ASSEMBLIES_VIEW'),
    ('SECRETARIA_ADMINISTRACION', 'ASSEMBLIES_MANAGE');

-- Consejo
INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('CONSEJO', 'COMMUNICATIONS_VIEW'),
    ('CONSEJO', 'ASSEMBLIES_VIEW');

-- Propietario (incluye voto en asambleas por Ley 675)
INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('PROPIETARIO', 'RESERVATIONS_VIEW_OWN'),
    ('PROPIETARIO', 'RESERVATIONS_MANAGE_OWN'),
    ('PROPIETARIO', 'COMMUNICATIONS_VIEW'),
    ('PROPIETARIO', 'PQR_VIEW_OWN'),
    ('PROPIETARIO', 'ASSEMBLIES_VIEW'),
    ('PROPIETARIO', 'ASSEMBLIES_VOTE');

-- Arrendatario y Residente (reservas, comunicaciones, pqr; asambleas solo si tienen poder o consulta)
INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('ARRENDATARIO', 'RESERVATIONS_VIEW_OWN'),
    ('ARRENDATARIO', 'RESERVATIONS_MANAGE_OWN'),
    ('ARRENDATARIO', 'COMMUNICATIONS_VIEW'),
    ('ARRENDATARIO', 'PQR_VIEW_OWN'),
    ('ARRENDATARIO', 'ASSEMBLIES_VIEW'),
    ('RESIDENTE', 'RESERVATIONS_VIEW_OWN'),
    ('RESIDENTE', 'RESERVATIONS_MANAGE_OWN'),
    ('RESIDENTE', 'COMMUNICATIONS_VIEW'),
    ('RESIDENTE', 'PQR_VIEW_OWN'),
    ('RESIDENTE', 'ASSEMBLIES_VIEW');

-- Portería y supervisión
INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('PORTERO', 'COMMUNICATIONS_VIEW'),
    ('SUPERVISOR_PORTERIA', 'COMMUNICATIONS_VIEW');

