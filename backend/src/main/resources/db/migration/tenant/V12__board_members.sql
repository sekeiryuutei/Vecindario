-- Migración tenant V12: junta directiva de la copropiedad.
-- Cada miembro se enlaza a una persona del directorio; se conserva el historial con borrado lógico.

CREATE TABLE board_members (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    person_id   uuid NOT NULL REFERENCES people (id),
    position    varchar(20) NOT NULL
                CHECK (position IN ('PRESIDENTE','VICEPRESIDENTE','SECRETARIO','TESORERO','VOCAL','REVISOR_FISCAL')),
    start_date  date NOT NULL,
    end_date    date,
    status      varchar(12) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    notes       varchar(500),
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    deleted_at  timestamptz,
    deleted_by  uuid,
    CONSTRAINT ck_board_member_dates CHECK (end_date IS NULL OR end_date >= start_date)
);

CREATE INDEX ix_board_members_person ON board_members (person_id) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ux_board_active_person ON board_members (person_id)
    WHERE deleted_at IS NULL AND status = 'ACTIVE';
CREATE UNIQUE INDEX ux_board_active_position ON board_members (position)
    WHERE deleted_at IS NULL AND status = 'ACTIVE' AND position <> 'VOCAL';
