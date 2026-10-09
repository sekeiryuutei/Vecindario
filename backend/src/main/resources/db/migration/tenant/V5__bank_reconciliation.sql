CREATE TABLE reconciliation_settings (
    id              smallint PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    auto_threshold  numeric(4,3) NOT NULL DEFAULT 0.900 CHECK (auto_threshold BETWEEN 0.500 AND 1.000),
    ambiguity_gap   numeric(4,3) NOT NULL DEFAULT 0.100 CHECK (ambiguity_gap BETWEEN 0.000 AND 0.500),
    auto_apply      boolean NOT NULL DEFAULT true,
    updated_at      timestamptz NOT NULL DEFAULT now(),
    updated_by      uuid
);
INSERT INTO reconciliation_settings (id) VALUES (1);

-- Perfil de formato bancario: cada banco entrega columnas distintas. Las columnas admiten alias separados por coma.
CREATE TABLE bank_import_profiles (
    id                     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name                   varchar(80) NOT NULL,
    bank_name              varchar(80),
    date_column            varchar(200) NOT NULL,
    amount_column          varchar(200),
    credit_column          varchar(200),
    debit_column           varchar(200),
    description_column     varchar(200),
    reference_column       varchar(200),
    counterparty_column    varchar(200),
    transaction_id_column  varchar(200),
    date_format            varchar(30) NOT NULL DEFAULT 'dd/MM/yyyy',
    decimal_separator      char(1) NOT NULL DEFAULT ',' CHECK (decimal_separator IN (',', '.')),
    active                 boolean NOT NULL DEFAULT true,
    created_at             timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_profile_amount CHECK (amount_column IS NOT NULL OR credit_column IS NOT NULL)
);
CREATE UNIQUE INDEX ux_import_profile_name ON bank_import_profiles (lower(name)) WHERE active;
INSERT INTO bank_import_profiles (name, date_column, amount_column, description_column, reference_column, counterparty_column,
                                  transaction_id_column, date_format, decimal_separator)
VALUES ('Genérico (fecha, valor, descripción)', 'fecha,fecha transaccion,fecha de transaccion', 'valor,monto,importe,credito',
        'descripcion,detalle,concepto,observaciones', 'referencia,ref,documento,no. documento', 'nombre,tercero,ordenante,pagador',
        'id,id transaccion,consecutivo,numero de transaccion', 'dd/MM/yyyy', ',');

CREATE TABLE bank_statement_imports (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    bank_name          varchar(80) NOT NULL,
    account_label      varchar(80) NOT NULL,
    profile_id         uuid REFERENCES bank_import_profiles (id),
    original_filename  varchar(255) NOT NULL,
    file_hash          varchar(64) NOT NULL,
    format             varchar(5) NOT NULL CHECK (format IN ('CSV','XLSX')),
    status             varchar(12) NOT NULL DEFAULT 'QUEUED' CHECK (status IN ('QUEUED','PROCESSING','COMPLETED','FAILED')),
    total_rows         integer NOT NULL DEFAULT 0,
    auto_matched       integer NOT NULL DEFAULT 0,
    manual_review      integer NOT NULL DEFAULT 0,
    ambiguous          integer NOT NULL DEFAULT 0,
    duplicates         integer NOT NULL DEFAULT 0,
    ignored            integer NOT NULL DEFAULT 0,
    error_message      varchar(500),
    uploaded_by        uuid,
    created_at         timestamptz NOT NULL DEFAULT now(),
    started_at         timestamptz,
    completed_at       timestamptz
);
-- El mismo archivo no se puede cargar dos veces (salvo que el intento anterior haya FALLADO).
CREATE UNIQUE INDEX ux_import_file ON bank_statement_imports (file_hash) WHERE status <> 'FAILED';

CREATE TABLE bank_statement_rows (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    import_id        uuid NOT NULL REFERENCES bank_statement_imports (id),
    row_number       integer NOT NULL,
    txn_date         date NOT NULL,
    amount           numeric(14,2) NOT NULL,
    description      varchar(500),
    reference        varchar(200),
    counterparty     varchar(200),
    bank_txn_id      varchar(100),
    raw              jsonb NOT NULL DEFAULT '{}'::jsonb,
    tx_hash          varchar(64) NOT NULL,
    status           varchar(14) NOT NULL CHECK (status IN ('AUTO_MATCHED','MANUAL_REVIEW','MATCHED','REJECTED','DUPLICATE','AMBIGUOUS','IGNORED')),
    match_score      numeric(4,3),
    unit_id          uuid REFERENCES property_units (id),
    payment_id       uuid REFERENCES payments (id),
    apply_count      integer NOT NULL DEFAULT 0,
    decided_by       uuid,
    decided_at       timestamptz,
    decision_reason  varchar(300),
    created_at       timestamptz NOT NULL DEFAULT now()
);
-- Identificador estable de la transacción: una misma transacción jamás se aplica dos veces, aunque venga en archivos que se solapan.
CREATE UNIQUE INDEX ux_bank_row_hash ON bank_statement_rows (tx_hash) WHERE status <> 'DUPLICATE';
CREATE INDEX ix_bank_rows_import ON bank_statement_rows (import_id, row_number);
CREATE INDEX ix_bank_rows_status ON bank_statement_rows (status);

CREATE TABLE bank_row_candidates (
    row_id   uuid NOT NULL REFERENCES bank_statement_rows (id),
    unit_id  uuid NOT NULL REFERENCES property_units (id),
    score    numeric(4,3) NOT NULL,
    signals  varchar(300) NOT NULL,
    PRIMARY KEY (row_id, unit_id)
);

-- Cada decisión (automática o manual) queda registrada: usuario, fecha, estado anterior y nuevo, motivo. Inmutable.
CREATE TABLE bank_row_decisions (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    row_id       uuid NOT NULL REFERENCES bank_statement_rows (id),
    action       varchar(20) NOT NULL,
    from_status  varchar(14),
    to_status    varchar(14) NOT NULL,
    unit_id      uuid,
    payment_id   uuid,
    reason       varchar(300),
    decided_by   uuid,
    decided_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_bank_decisions_row ON bank_row_decisions (row_id, decided_at);

CREATE FUNCTION bank_row_decisions_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'bank_row_decisions es de solo inserción';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_bank_decisions_immutable
    BEFORE UPDATE OR DELETE ON bank_row_decisions
    FOR EACH ROW EXECUTE FUNCTION bank_row_decisions_immutable();

-- Aprendizaje: a qué inmueble se asignó manualmente cada pagador (mejora futuras coincidencias).
CREATE TABLE bank_counterparty_hints (
    hint_key         varchar(200) PRIMARY KEY,
    unit_id          uuid NOT NULL REFERENCES property_units (id),
    confirmed_count  integer NOT NULL DEFAULT 1,
    updated_at       timestamptz NOT NULL DEFAULT now()
);
