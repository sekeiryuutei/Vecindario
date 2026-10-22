-- Migración tenant V16: expediente del inmueble.
-- Los documentos pueden asociarse opcionalmente a un inmueble y se agregan notas internas o públicas.

ALTER TABLE documents ADD COLUMN unit_id uuid REFERENCES property_units (id);
CREATE INDEX ix_documents_unit ON documents (unit_id) WHERE unit_id IS NOT NULL;

CREATE TABLE unit_file_notes (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    unit_id     uuid NOT NULL REFERENCES property_units (id),
    title       varchar(160) NOT NULL,
    body        varchar(2000) NOT NULL,
    internal    boolean NOT NULL DEFAULT true,
    created_by  uuid NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    deleted_at  timestamptz,
    deleted_by  uuid
);

CREATE INDEX ix_unit_file_notes_unit ON unit_file_notes (unit_id) WHERE deleted_at IS NULL;
