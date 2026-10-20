-- Migración global V10: permisos operativos de portería
-- El personal de portería necesita consultar inmuebles para registrar visitantes
-- presenciales, paquetería y consultas de residentes desde el portal operativo.

INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('PORTERO', 'PROPERTIES_VIEW'),
    ('SUPERVISOR_PORTERIA', 'PROPERTIES_VIEW')
ON CONFLICT DO NOTHING;
