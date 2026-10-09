-- Migración global V9: Permisos adicionales Fase 5 (Proveedores, Presupuesto, Exportación de reportes)

INSERT INTO permissions (code, description) VALUES
    ('SUPPLIERS_VIEW', 'Consultar catálogo de proveedores y contratistas'),
    ('SUPPLIERS_MANAGE', 'Gestionar proveedores, evaluaciones y contratos'),
    ('BUDGET_VIEW', 'Consultar presupuestos anuales y balances de ejecución'),
    ('BUDGET_MANAGE', 'Gestionar presupuestos anuales, rubros y ejecuciones de gasto'),
    ('REPORTS_EXPORT', 'Exportar reportes en CSV, XLSX y PDF')
ON CONFLICT (code) DO NOTHING;

-- Asignación a roles
INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('ADMINISTRADOR', 'SUPPLIERS_VIEW'),
    ('ADMINISTRADOR', 'SUPPLIERS_MANAGE'),
    ('ADMINISTRADOR', 'BUDGET_VIEW'),
    ('ADMINISTRADOR', 'BUDGET_MANAGE'),
    ('ADMINISTRADOR', 'REPORTS_EXPORT'),
    ('CONSEJO', 'SUPPLIERS_VIEW'),
    ('CONSEJO', 'BUDGET_VIEW'),
    ('CONSEJO', 'REPORTS_EXPORT'),
    ('CONTADOR', 'SUPPLIERS_VIEW'),
    ('CONTADOR', 'BUDGET_VIEW'),
    ('CONTADOR', 'BUDGET_MANAGE'),
    ('CONTADOR', 'REPORTS_EXPORT'),
    ('PROPIETARIO', 'BUDGET_VIEW')
ON CONFLICT DO NOTHING;
