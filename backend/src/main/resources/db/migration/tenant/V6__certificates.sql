-- Migración tenant V6: registro inmutable de certificados emitidos

CREATE TABLE issued_certificates (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    verification_code  varchar(50) NOT NULL UNIQUE,
    certificate_type   varchar(30) NOT NULL CHECK (certificate_type IN ('PAZ_Y_SALVO', 'ESTADO_CUENTA', 'CERTIFICADO_PROPIEDAD')),
    unit_id            uuid NOT NULL REFERENCES property_units (id),
    person_id          uuid REFERENCES people (id),
    issued_by          uuid,
    issued_at          timestamptz NOT NULL DEFAULT now(),
    expires_at         timestamptz,
    recipient_name     varchar(200) NOT NULL,
    recipient_doc_type varchar(10),
    recipient_doc_num  varchar(40),
    unit_identifier    varchar(60) NOT NULL,
    status             varchar(20) NOT NULL DEFAULT 'VALID' CHECK (status IN ('VALID', 'REVOKED', 'EXPIRED')),
    snapshot_data      jsonb NOT NULL DEFAULT '{}'::jsonb,
    content_hash       varchar(64) NOT NULL
);
CREATE INDEX ix_certificates_unit ON issued_certificates (unit_id, issued_at DESC);
CREATE INDEX ix_certificates_code ON issued_certificates (verification_code);

