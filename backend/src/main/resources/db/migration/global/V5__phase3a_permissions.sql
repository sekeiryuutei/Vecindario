INSERT INTO permissions (code, description) VALUES
 ('FINANCE_VIEW_OWN', 'Ver el estado de cuenta de los inmuebles propios');

-- Solo el propietario por defecto. Arrendatario/residente NO ven finanzas salvo que la copropiedad lo configure.
INSERT INTO role_permissions (role_code, permission_code) VALUES ('PROPIETARIO', 'FINANCE_VIEW_OWN');
