-- Migración global V11: permisos de nómina administrativa.
-- El administrador recibe todos los permisos por código; contabilidad puede
-- parametrizar y operar la nómina, mientras secretaría y consejo solo consultan.

INSERT INTO permissions (code, description) VALUES
 ('PAYROLL_VIEW', 'Ver empleados, parámetros y corridas de nómina'),
 ('PAYROLL_MANAGE', 'Administrar empleados, parámetros y corridas de nómina')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code) VALUES
 ('CONTADOR', 'PAYROLL_VIEW'),
 ('CONTADOR', 'PAYROLL_MANAGE'),
 ('SECRETARIA_ADMINISTRACION', 'PAYROLL_VIEW'),
 ('CONSEJO', 'PAYROLL_VIEW')
ON CONFLICT DO NOTHING;
