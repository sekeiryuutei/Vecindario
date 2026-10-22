-- Migración tenant V14: capacitaciones comunitarias e inscripciones.

CREATE TABLE training_sessions (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    title         varchar(160) NOT NULL,
    description   varchar(1000),
    trainer_name  varchar(160),
    modality      varchar(12) NOT NULL DEFAULT 'IN_PERSON'
                  CHECK (modality IN ('IN_PERSON','VIRTUAL','HYBRID')),
    location      varchar(200),
    meeting_link  varchar(500),
    starts_at     timestamptz NOT NULL,
    ends_at       timestamptz NOT NULL,
    capacity      int NOT NULL CHECK (capacity BETWEEN 1 AND 500),
    status        varchar(20) NOT NULL DEFAULT 'SCHEDULED'
                  CHECK (status IN ('SCHEDULED','CANCELLED','COMPLETED')),
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    deleted_at    timestamptz,
    deleted_by    uuid,
    CONSTRAINT ck_training_dates CHECK (ends_at > starts_at)
);

CREATE INDEX ix_training_sessions_starts ON training_sessions (starts_at) WHERE deleted_at IS NULL;

CREATE TABLE training_enrollments (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id  uuid NOT NULL REFERENCES training_sessions (id) ON DELETE CASCADE,
    person_id   uuid NOT NULL REFERENCES people (id),
    unit_id     uuid REFERENCES property_units (id),
    status      varchar(20) NOT NULL DEFAULT 'ENROLLED'
                CHECK (status IN ('ENROLLED','ATTENDED','ABSENT','CANCELLED')),
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_training_enrollment UNIQUE (session_id, person_id)
);

CREATE INDEX ix_training_enrollments_person ON training_enrollments (person_id);
CREATE INDEX ix_training_enrollments_session ON training_enrollments (session_id);
