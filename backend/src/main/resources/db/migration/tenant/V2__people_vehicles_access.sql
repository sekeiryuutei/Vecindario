-- Personas: independientes de los inmuebles. user_id enlaza (opcionalmente) con un usuario global de la plataforma.
CREATE TABLE people (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    document_type    varchar(10) CHECK (document_type IS NULL OR document_type IN ('CC','CE','NIT','PAS','TI','OTRO')),
    document_number  varchar(30),
    full_name        varchar(200) NOT NULL,
    email            varchar(254),
    phone            varchar(40),
    user_id          uuid,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    deleted_at       timestamptz,
    deleted_by       uuid
);
CREATE UNIQUE INDEX ux_people_document ON people (document_type, document_number)
    WHERE deleted_at IS NULL AND document_number IS NOT NULL;
CREATE UNIQUE INDEX ux_people_user ON people (user_id) WHERE deleted_at IS NULL AND user_id IS NOT NULL;

-- Relación N:N persona-inmueble (una persona puede tener muchos inmuebles y un inmueble varias personas).
CREATE TABLE unit_relations (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    unit_id        uuid NOT NULL REFERENCES property_units (id),
    person_id      uuid NOT NULL REFERENCES people (id),
    relation_type  varchar(10) NOT NULL CHECK (relation_type IN ('OWNER','TENANT','RESIDENT')),
    start_date     date,
    end_date       date,
    status         varchar(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','ENDED')),
    created_at     timestamptz NOT NULL DEFAULT now(),
    created_by     uuid,
    ended_at       timestamptz,
    ended_by       uuid
);
CREATE UNIQUE INDEX ux_unit_relations_active ON unit_relations (unit_id, person_id, relation_type) WHERE status = 'ACTIVE';
CREATE INDEX ix_unit_relations_person ON unit_relations (person_id) WHERE status = 'ACTIVE';
CREATE INDEX ix_unit_relations_unit ON unit_relations (unit_id) WHERE status = 'ACTIVE';

-- Tipos de vehículo configurables por copropiedad.
CREATE TABLE vehicle_types (
    code            varchar(30) PRIMARY KEY CHECK (code ~ '^[A-Z][A-Z0-9_]{1,29}$'),
    name            varchar(60) NOT NULL,
    active          boolean NOT NULL DEFAULT true,
    requires_plate  boolean NOT NULL DEFAULT true
);
INSERT INTO vehicle_types (code, name, active, requires_plate) VALUES
 ('CARRO', 'Carro', true, true), ('MOTO', 'Moto', true, true), ('BICICLETA', 'Bicicleta', true, false),
 ('PATINETA', 'Patineta', true, false), ('OTRO', 'Otro', true, false);

-- Límite por defecto por tipo y excepción por inmueble (p. ej. apartamento con sótano privado).
CREATE TABLE vehicle_type_limits (
    vehicle_type_code  varchar(30) PRIMARY KEY REFERENCES vehicle_types (code),
    max_per_unit       integer NOT NULL CHECK (max_per_unit >= 0)
);
INSERT INTO vehicle_type_limits (vehicle_type_code, max_per_unit) VALUES
 ('CARRO', 1), ('MOTO', 1), ('BICICLETA', 2), ('PATINETA', 2), ('OTRO', 1);

CREATE TABLE unit_vehicle_limits (
    unit_id            uuid NOT NULL REFERENCES property_units (id),
    vehicle_type_code  varchar(30) NOT NULL REFERENCES vehicle_types (code),
    max_count          integer NOT NULL CHECK (max_count >= 0),
    PRIMARY KEY (unit_id, vehicle_type_code)
);

CREATE TABLE parking_spaces (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code        varchar(30) NOT NULL,
    kind        varchar(15) NOT NULL CHECK (kind IN ('PUBLICO','PRIVADO','CUBIERTO','SOTANO','VISITANTE','TEMPORAL')),
    unit_id     uuid REFERENCES property_units (id),
    status      varchar(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    created_at  timestamptz NOT NULL DEFAULT now(),
    deleted_at  timestamptz
);
CREATE UNIQUE INDEX ux_parking_code ON parking_spaces (lower(code)) WHERE deleted_at IS NULL;

-- status = autorización del registro; presence = si está físicamente dentro. Son conceptos distintos.
CREATE TABLE vehicles (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    vehicle_type_code  varchar(30) NOT NULL REFERENCES vehicle_types (code),
    plate              varchar(12),
    brand              varchar(60),
    model              varchar(60),
    color              varchar(40),
    model_year         integer CHECK (model_year IS NULL OR model_year BETWEEN 1900 AND 2100),
    owner_person_id    uuid REFERENCES people (id),
    unit_id            uuid NOT NULL REFERENCES property_units (id),
    parking_space_id   uuid REFERENCES parking_spaces (id),
    status             varchar(12) NOT NULL DEFAULT 'AUTHORIZED' CHECK (status IN ('AUTHORIZED','BLOCKED')),
    presence           varchar(8)  NOT NULL DEFAULT 'OUTSIDE' CHECK (presence IN ('INSIDE','OUTSIDE')),
    is_primary         boolean NOT NULL DEFAULT false,
    metadata           jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    deleted_at         timestamptz,
    deleted_by         uuid
);
CREATE UNIQUE INDEX ux_vehicles_plate ON vehicles (upper(plate)) WHERE deleted_at IS NULL AND plate IS NOT NULL;
CREATE INDEX ix_vehicles_unit ON vehicles (unit_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_vehicles_inside ON vehicles (presence) WHERE presence = 'INSIDE' AND deleted_at IS NULL;

CREATE TABLE vehicle_access_events (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    vehicle_id      uuid NOT NULL REFERENCES vehicles (id),
    unit_id         uuid NOT NULL,
    plate           varchar(12),
    event_type      varchar(5) NOT NULL CHECK (event_type IN ('ENTRY','EXIT')),
    occurred_at     timestamptz NOT NULL DEFAULT now(),
    guard_user_id   uuid,
    camera_id       varchar(60),
    source          varchar(10) NOT NULL DEFAULT 'MANUAL' CHECK (source IN ('MANUAL','CAMERA','LPR')),
    method          varchar(20),
    entry_event_id  uuid REFERENCES vehicle_access_events (id),
    metadata        jsonb NOT NULL DEFAULT '{}'::jsonb
);
-- Una salida por cada entrada: protege contra doble registro concurrente.
CREATE UNIQUE INDEX ux_exit_per_entry ON vehicle_access_events (entry_event_id) WHERE entry_event_id IS NOT NULL;
CREATE INDEX ix_access_events_vehicle ON vehicle_access_events (vehicle_id, occurred_at DESC);
CREATE INDEX ix_access_events_time ON vehicle_access_events (occurred_at DESC);

CREATE TABLE security_alerts (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_type     varchar(30) NOT NULL,
    vehicle_id     uuid,
    plate          varchar(12),
    unit_id        uuid,
    message        varchar(300) NOT NULL,
    guard_user_id  uuid,
    created_at     timestamptz NOT NULL DEFAULT now(),
    resolved_at    timestamptz,
    resolved_by    uuid
);
CREATE INDEX ix_security_alerts_open ON security_alerts (created_at DESC) WHERE resolved_at IS NULL;
