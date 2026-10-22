-- Migración tenant V15: directorio de personal operativo de la copropiedad.
-- Es independiente de la nómina: aquí solo se publica la información operativa del equipo.

CREATE TABLE staff_members (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name       varchar(200) NOT NULL,
    document_type   varchar(20) NOT NULL DEFAULT 'CC',
    document_number varchar(30) NOT NULL,
    position        varchar(100) NOT NULL,
    area            varchar(100),
    phone           varchar(40),
    email           varchar(254),
    shift           varchar(80),
    start_date      date NOT NULL,
    end_date        date,
    status          varchar(12) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    notes           varchar(500),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    deleted_at      timestamptz,
    deleted_by      uuid,
    CONSTRAINT ck_staff_dates CHECK (end_date IS NULL OR end_date >= start_date)
);

CREATE UNIQUE INDEX ux_staff_document ON staff_members (document_type, document_number)
    WHERE deleted_at IS NULL;
CREATE INDEX ix_staff_status ON staff_members (status) WHERE deleted_at IS NULL;
CREATE INDEX ix_staff_name ON staff_members (lower(full_name)) WHERE deleted_at IS NULL;
