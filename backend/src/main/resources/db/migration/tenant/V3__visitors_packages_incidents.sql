-- Invitaciones de visitantes (el QR se guarda solo como hash SHA-256).
CREATE TABLE visitor_invitations (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    unit_id          uuid NOT NULL REFERENCES property_units (id),
    created_by       uuid NOT NULL,
    visitor_name     varchar(200) NOT NULL,
    document_number  varchar(30),
    phone            varchar(40),
    plate            varchar(12),
    people_count     integer NOT NULL DEFAULT 1 CHECK (people_count BETWEEN 1 AND 50),
    valid_from       timestamptz NOT NULL,
    valid_to         timestamptz NOT NULL,
    max_entries      integer NOT NULL DEFAULT 1 CHECK (max_entries BETWEEN 1 AND 20),
    used_count       integer NOT NULL DEFAULT 0 CHECK (used_count >= 0),
    qr_hash          varchar(64) NOT NULL,
    status           varchar(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','CANCELLED')),
    notes            varchar(300),
    created_at       timestamptz NOT NULL DEFAULT now(),
    cancelled_at     timestamptz,
    CONSTRAINT ck_invitation_window CHECK (valid_to > valid_from)
);
CREATE UNIQUE INDEX ux_invitation_qr ON visitor_invitations (qr_hash);
CREATE INDEX ix_invitations_unit ON visitor_invitations (unit_id, valid_to DESC);

-- Visitas reales: con invitación (entran directo) o sin invitación (esperan la decisión del residente).
CREATE TABLE visits (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    invitation_id    uuid REFERENCES visitor_invitations (id),
    unit_id          uuid NOT NULL REFERENCES property_units (id),
    visitor_name     varchar(200) NOT NULL,
    document_number  varchar(30),
    phone            varchar(40),
    plate            varchar(12),
    people_count     integer NOT NULL DEFAULT 1 CHECK (people_count BETWEEN 1 AND 50),
    source           varchar(10) NOT NULL CHECK (source IN ('INVITATION','WALK_IN')),
    status           varchar(12) NOT NULL CHECK (status IN ('PENDING_AUTH','AUTHORIZED','REJECTED','INSIDE','LEFT')),
    requested_by     uuid,
    requested_at     timestamptz NOT NULL DEFAULT now(),
    decided_by       uuid,
    decided_at       timestamptz,
    decision_note    varchar(300),
    entry_time       timestamptz,
    entry_guard      uuid,
    exit_time        timestamptz,
    exit_guard       uuid,
    note             varchar(300)
);
CREATE INDEX ix_visits_status ON visits (status, requested_at DESC);
CREATE INDEX ix_visits_unit ON visits (unit_id, requested_at DESC);

CREATE TABLE packages (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    unit_id          uuid NOT NULL REFERENCES property_units (id),
    recipient_name   varchar(200) NOT NULL,
    carrier          varchar(80),
    tracking_number  varchar(80),
    description      varchar(300),
    status           varchar(10) NOT NULL DEFAULT 'RECEIVED' CHECK (status IN ('RECEIVED','NOTIFIED','DELIVERED','RETURNED')),
    received_at      timestamptz NOT NULL DEFAULT now(),
    received_by      uuid,
    notified_at      timestamptz,
    delivered_at     timestamptz,
    delivered_by     uuid,
    delivered_to     varchar(200),
    returned_at      timestamptz,
    returned_by      uuid,
    note             varchar(300)
);
CREATE INDEX ix_packages_unit_status ON packages (unit_id, status);
CREATE INDEX ix_packages_pending ON packages (received_at DESC) WHERE status IN ('RECEIVED','NOTIFIED');

CREATE TABLE incidents (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    category     varchar(20) NOT NULL CHECK (category IN ('DANO','SEGURIDAD','RUIDO','VEHICULO','VISITANTE','INFRAESTRUCTURA','EMERGENCIA','OTRO')),
    description  varchar(2000) NOT NULL,
    location     varchar(150),
    occurred_at  timestamptz NOT NULL DEFAULT now(),
    reported_by  uuid NOT NULL,
    assigned_to  uuid,
    status       varchar(12) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','IN_REVIEW','CLOSED')),
    resolution   varchar(500),
    closed_at    timestamptz,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_incidents_status ON incidents (status, occurred_at DESC);
CREATE INDEX ix_incidents_category ON incidents (category, occurred_at DESC);
