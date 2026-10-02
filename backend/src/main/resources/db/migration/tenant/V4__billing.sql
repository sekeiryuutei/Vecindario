-- Configuración financiera de la copropiedad (una sola fila).
CREATE TABLE billing_settings (
    id                              smallint PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    allocation_order                varchar(200) NOT NULL DEFAULT 'INTEREST,EXTRAORDINARY,ORDINARY,FINE,OTHER',
    oldest_first                    boolean NOT NULL DEFAULT true,
    interest_enabled                boolean NOT NULL DEFAULT false,
    interest_monthly_rate           numeric(7,4) NOT NULL DEFAULT 0 CHECK (interest_monthly_rate >= 0 AND interest_monthly_rate <= 10),
    grace_days                      integer NOT NULL DEFAULT 0 CHECK (grace_days BETWEEN 0 AND 365),
    block_reservations_when_overdue boolean NOT NULL DEFAULT false,
    overdue_days_for_block          integer NOT NULL DEFAULT 30 CHECK (overdue_days_for_block BETWEEN 0 AND 3650),
    updated_at                      timestamptz NOT NULL DEFAULT now(),
    updated_by                      uuid
);
INSERT INTO billing_settings (id) VALUES (1);

CREATE TABLE charges (
    id                     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    unit_id                uuid NOT NULL REFERENCES property_units (id),
    concept_type           varchar(15) NOT NULL CHECK (concept_type IN ('ORDINARY','EXTRAORDINARY','INTEREST','FINE','OTHER')),
    description            varchar(300) NOT NULL,
    period                 date,
    issue_date             date NOT NULL,
    due_date               date NOT NULL,
    amount                 numeric(14,2) NOT NULL CHECK (amount > 0),
    adjusted_amount        numeric(14,2) NOT NULL DEFAULT 0,
    paid_amount            numeric(14,2) NOT NULL DEFAULT 0 CHECK (paid_amount >= 0),
    interest_accrued_until date,
    voided_at              timestamptz,
    voided_by              uuid,
    void_reason            varchar(300),
    created_by             uuid,
    created_at             timestamptz NOT NULL DEFAULT now(),
    -- Garantía a nivel de base de datos: jamás se puede abonar más de lo que se debe.
    CONSTRAINT ck_charge_not_overpaid CHECK (amount + adjusted_amount >= paid_amount),
    CONSTRAINT ck_charge_period_first_day CHECK (period IS NULL OR EXTRACT(day FROM period) = 1)
);
-- Una sola cuota ordinaria por inmueble y periodo (hace idempotente la facturación masiva).
CREATE UNIQUE INDEX ux_charge_ordinary_period ON charges (unit_id, period)
    WHERE concept_type = 'ORDINARY' AND voided_at IS NULL AND period IS NOT NULL;
CREATE INDEX ix_charges_unit_due ON charges (unit_id, due_date) WHERE voided_at IS NULL;

CREATE TABLE payments (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    unit_id           uuid NOT NULL REFERENCES property_units (id),
    amount            numeric(14,2) NOT NULL CHECK (amount > 0),
    unapplied_amount  numeric(14,2) NOT NULL DEFAULT 0 CHECK (unapplied_amount >= 0 AND unapplied_amount <= amount),
    payment_date      date NOT NULL,
    method            varchar(10) NOT NULL CHECK (method IN ('CASH','TRANSFER','WOMPI','OTHER')),
    source            varchar(20) NOT NULL DEFAULT 'MANUAL' CHECK (source IN ('MANUAL','BANK_RECONCILIATION','WOMPI')),
    reference         varchar(100),
    payer_name        varchar(200),
    notes             varchar(300),
    status            varchar(10) NOT NULL DEFAULT 'APPLIED' CHECK (status IN ('APPLIED','REVERSED')),
    idempotency_key   varchar(100),
    created_by        uuid,
    created_at        timestamptz NOT NULL DEFAULT now(),
    reversed_at       timestamptz,
    reversed_by       uuid,
    reverse_reason    varchar(300)
);
CREATE UNIQUE INDEX ux_payments_idempotency ON payments (idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX ix_payments_unit ON payments (unit_id, payment_date DESC);
CREATE INDEX ix_payments_credit ON payments (unit_id, payment_date) WHERE status = 'APPLIED' AND unapplied_amount > 0;

CREATE TABLE payment_allocations (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id   uuid NOT NULL REFERENCES payments (id),
    charge_id    uuid NOT NULL REFERENCES charges (id),
    amount       numeric(14,2) NOT NULL CHECK (amount > 0),
    created_at   timestamptz NOT NULL DEFAULT now(),
    reversed_at  timestamptz
);
CREATE INDEX ix_alloc_payment ON payment_allocations (payment_id);
CREATE INDEX ix_alloc_charge ON payment_allocations (charge_id);

-- Libro de movimientos: cada evento financiero deja una línea inmutable. Positivo = aumenta la deuda.
CREATE TABLE ledger_entries (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    unit_id       uuid NOT NULL REFERENCES property_units (id),
    entry_type    varchar(20) NOT NULL CHECK (entry_type IN ('CHARGE','PAYMENT','ADJUSTMENT_CREDIT','ADJUSTMENT_DEBIT','VOID','REVERSAL')),
    amount        numeric(14,2) NOT NULL,
    entry_date    date NOT NULL,
    charge_id     uuid,
    payment_id    uuid,
    concept_type  varchar(15),
    description   varchar(300),
    reason        varchar(300),
    created_by    uuid,
    created_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_ledger_unit_date ON ledger_entries (unit_id, entry_date, created_at);

CREATE FUNCTION ledger_entries_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'ledger_entries es de solo inserción: corrige con ajustes o reversiones';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_immutable
    BEFORE UPDATE OR DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION ledger_entries_immutable();
