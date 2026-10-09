-- Migración global V8: Permisos de visualización de documentos para residentes y propietarios

INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('PROPIETARIO', 'DOCUMENTS_VIEW'),
    ('RESIDENTE', 'DOCUMENTS_VIEW'),
    ('ARRENDATARIO', 'DOCUMENTS_VIEW')
ON CONFLICT DO NOTHING;

