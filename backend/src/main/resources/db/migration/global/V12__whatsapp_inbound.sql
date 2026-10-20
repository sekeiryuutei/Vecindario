-- Mensajes entrantes de WhatsApp. La tabla es global porque el webhook llega
-- antes de conocer la copropiedad; el tenant se resuelve por el teléfono de la
-- persona registrada. message_id es la clave de idempotencia de Meta.
CREATE TABLE whatsapp_inbound_messages (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id     varchar(150) NOT NULL UNIQUE,
    from_phone     varchar(40) NOT NULL,
    tenant_id      uuid REFERENCES tenants(id) ON DELETE SET NULL,
    person_id      uuid,
    body           text,
    status         varchar(20) NOT NULL DEFAULT 'RECEIVED'
                   CHECK (status IN ('RECEIVED', 'PROCESSED', 'IGNORED', 'FAILED')),
    response       text,
    error_detail   text,
    created_at     timestamptz NOT NULL DEFAULT now(),
    processed_at   timestamptz
);
CREATE INDEX ix_whatsapp_inbound_created ON whatsapp_inbound_messages (created_at DESC);
CREATE INDEX ix_whatsapp_inbound_tenant ON whatsapp_inbound_messages (tenant_id, created_at DESC);
CREATE INDEX ix_whatsapp_inbound_phone ON whatsapp_inbound_messages (from_phone, created_at DESC);
