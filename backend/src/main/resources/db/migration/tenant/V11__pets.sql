-- Migración tenant V11: registro de mascotas por inmueble.
-- Una mascota pertenece a un inmueble y se conserva como historial (borrado lógico).

CREATE TABLE pets (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    unit_id            uuid NOT NULL REFERENCES property_units (id),
    name               varchar(80) NOT NULL,
    species            varchar(10) NOT NULL CHECK (species IN ('PERRO','GATO','AVE','OTRO')),
    breed              varchar(80),
    color              varchar(40),
    birth_date         date,
    weight_kg          numeric(5,2) CHECK (weight_kg IS NULL OR (weight_kg > 0 AND weight_kg <= 200)),
    microchip          varchar(40),
    vaccinated         boolean NOT NULL DEFAULT false,
    vaccine_expires_on date,
    notes              varchar(500),
    status             varchar(12) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    deleted_at         timestamptz,
    deleted_by         uuid
);

CREATE INDEX ix_pets_unit ON pets (unit_id) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ux_pets_microchip ON pets (upper(microchip))
    WHERE deleted_at IS NULL AND microchip IS NOT NULL;
