-- Se aplica DENTRO del schema de cada copropiedad (tenant_<slug>). Nombres sin calificar a propósito.

CREATE TABLE property_units (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    unit_type     varchar(20)  NOT NULL CHECK (unit_type IN ('APARTAMENTO','CASA','LOCAL','PARQUEADERO','DEPOSITO','OTRO')),
    identifier    varchar(60)  NOT NULL,
    unit_number   varchar(30),
    tower         varchar(30),
    floor_number  integer,
    coefficient   numeric(9,6) CHECK (coefficient IS NULL OR (coefficient >= 0 AND coefficient <= 100)),
    area_m2       numeric(10,2) CHECK (area_m2 IS NULL OR area_m2 >= 0),
    status        varchar(10)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    deleted_at    timestamptz,
    deleted_by    uuid
);
CREATE UNIQUE INDEX ux_property_units_identifier ON property_units (lower(identifier)) WHERE deleted_at IS NULL;
CREATE INDEX ix_property_units_tower ON property_units (tower) WHERE deleted_at IS NULL;

-- Ajustes de permisos por rol propios de esta copropiedad (sobre la base global).
CREATE TABLE role_permission_overrides (
    role_code        varchar(40) NOT NULL,
    permission_code  varchar(60) NOT NULL,
    granted          boolean     NOT NULL,
    updated_by       uuid,
    updated_at       timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (role_code, permission_code)
);
