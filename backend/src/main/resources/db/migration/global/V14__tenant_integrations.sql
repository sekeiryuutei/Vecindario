-- Credenciales de integraciones por copropiedad. El contenido se guarda
-- cifrado con AES-GCM; la llave maestra vive fuera de la base de datos.
CREATE TABLE tenant_integrations (
    tenant_id         uuid        NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    provider          varchar(20) NOT NULL CHECK (provider IN ('WOMPI', 'AI')),
    config_ciphertext text        NOT NULL,
    updated_by        uuid,
    updated_at        timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, provider)
);

CREATE INDEX ix_tenant_integrations_updated ON tenant_integrations (updated_at DESC);
