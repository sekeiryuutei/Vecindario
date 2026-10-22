-- Migración tenant V13: citas de los residentes con la administración.
-- La administración confirma, reprograma, completa o cancela; el residente solicita y cancela las suyas.

CREATE TABLE appointments (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    unit_id             uuid NOT NULL REFERENCES property_units (id),
    person_id           uuid NOT NULL REFERENCES people (id),
    subject             varchar(160) NOT NULL,
    reason              varchar(1000) NOT NULL,
    scheduled_at        timestamptz NOT NULL,
    duration_minutes    int NOT NULL DEFAULT 30 CHECK (duration_minutes BETWEEN 15 AND 120),
    modality            varchar(12) NOT NULL DEFAULT 'IN_PERSON'
                        CHECK (modality IN ('IN_PERSON','VIRTUAL','PHONE')),
    location            varchar(200),
    meeting_link        varchar(500),
    status              varchar(20) NOT NULL DEFAULT 'REQUESTED'
                        CHECK (status IN ('REQUESTED','CONFIRMED','COMPLETED','CANCELLED','NO_SHOW')),
    admin_notes         varchar(1000),
    cancellation_reason varchar(500),
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    deleted_at          timestamptz,
    deleted_by          uuid
);

CREATE INDEX ix_appointments_scheduled ON appointments (scheduled_at) WHERE deleted_at IS NULL;
CREATE INDEX ix_appointments_person ON appointments (person_id) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ux_appointments_slot ON appointments (scheduled_at)
    WHERE deleted_at IS NULL AND status IN ('REQUESTED','CONFIRMED');
