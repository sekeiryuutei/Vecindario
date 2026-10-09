-- Migración global V6: intenciones de pago en línea (Wompi), verificación pública de certificados y permisos de Fase 3c

CREATE TABLE online_payment_intents (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    reference         varchar(100) NOT NULL UNIQUE,
    tenant_id         uuid NOT NULL REFERENCES tenants (id),
    unit_id           uuid NOT NULL,
    user_id           uuid REFERENCES users (id),
    amount_in_cents   bigint NOT NULL CHECK (amount_in_cents > 0),
    currency          char(3) NOT NULL DEFAULT 'COP',
    signature         varchar(128) NOT NULL,
    status            varchar(20) NOT NULL DEFAULT 'PENDING'
                      CHECK (status IN ('PENDING', 'APPROVED', 'DECLINED', 'VOIDED', 'ERROR')),
    wompi_tx_id       varchar(100),
    payment_method    varchar(50),
    fee_in_cents      bigint,
    error_message     varchar(500),
    payment_id        uuid,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_online_intents_tenant_unit ON online_payment_intents (tenant_id, unit_id, created_at DESC);
CREATE INDEX ix_online_intents_wompi_tx ON online_payment_intents (wompi_tx_id) WHERE wompi_tx_id IS NOT NULL;

CREATE TABLE certificate_verifications (
    verification_code  varchar(50) PRIMARY KEY,
    tenant_id          uuid NOT NULL REFERENCES tenants (id),
    certificate_type   varchar(30) NOT NULL CHECK (certificate_type IN ('PAZ_Y_SALVO', 'ESTADO_CUENTA', 'CERTIFICADO_PROPIEDAD')),
    unit_identifier    varchar(60) NOT NULL,
    recipient_name     varchar(200) NOT NULL,
    issued_at          timestamptz NOT NULL DEFAULT now(),
    status             varchar(20) NOT NULL DEFAULT 'VALID' CHECK (status IN ('VALID', 'REVOKED', 'EXPIRED')),
    content_hash       varchar(64) NOT NULL
);
CREATE INDEX ix_cert_verifications_tenant ON certificate_verifications (tenant_id, issued_at DESC);

-- Nuevos permisos de Fase 3c
INSERT INTO permissions (code, description) VALUES
    ('PAYMENTS_ONLINE_CREATE', 'Crear intención de pago en línea para inmuebles propios'),
    ('CERTIFICATES_VIEW_OWN', 'Descargar certificados de inmuebles propios'),
    ('CERTIFICATES_MANAGE', 'Generar y consultar certificados de cualquier inmueble');

-- Asignación de permisos por rol
INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('PROPIETARIO', 'PAYMENTS_ONLINE_CREATE'),
    ('RESIDENTE', 'PAYMENTS_ONLINE_CREATE'),
    ('ARRENDATARIO', 'PAYMENTS_ONLINE_CREATE'),
    ('PROPIETARIO', 'CERTIFICATES_VIEW_OWN'),
    ('RESIDENTE', 'CERTIFICATES_VIEW_OWN'),
    ('ARRENDATARIO', 'CERTIFICATES_VIEW_OWN'),
    ('ADMINISTRADOR', 'CERTIFICATES_MANAGE'),
    ('SECRETARIA_ADMINISTRACION', 'CERTIFICATES_MANAGE'),
    ('CONTADOR', 'CERTIFICATES_MANAGE');

