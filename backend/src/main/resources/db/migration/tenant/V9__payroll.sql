-- Nómina administrativa: parámetros, empleados y corridas mensuales.
-- Los porcentajes son configurables por copropiedad; el sistema no certifica
-- cumplimiento laboral ni reemplaza la liquidación de un contador.

CREATE TABLE payroll_settings (
    id                         smallint PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    currency                   char(3) NOT NULL DEFAULT 'COP',
    periodicity                varchar(20) NOT NULL DEFAULT 'MONTHLY'
                               CHECK (periodicity IN ('MONTHLY', 'BIWEEKLY')),
    base_days                  int NOT NULL DEFAULT 30 CHECK (base_days BETWEEN 1 AND 31),
    health_employee_rate       numeric(5,2) NOT NULL DEFAULT 4.00 CHECK (health_employee_rate BETWEEN 0 AND 100),
    pension_employee_rate      numeric(5,2) NOT NULL DEFAULT 4.00 CHECK (pension_employee_rate BETWEEN 0 AND 100),
    health_employer_rate       numeric(5,2) NOT NULL DEFAULT 8.50 CHECK (health_employer_rate BETWEEN 0 AND 100),
    pension_employer_rate      numeric(5,2) NOT NULL DEFAULT 12.00 CHECK (pension_employer_rate BETWEEN 0 AND 100),
    arl_rate                   numeric(5,2) NOT NULL DEFAULT 0.522 CHECK (arl_rate BETWEEN 0 AND 100),
    compensation_rate          numeric(5,2) NOT NULL DEFAULT 3.00 CHECK (compensation_rate BETWEEN 0 AND 100),
    sena_rate                  numeric(5,2) NOT NULL DEFAULT 2.00 CHECK (sena_rate BETWEEN 0 AND 100),
    icbf_rate                  numeric(5,2) NOT NULL DEFAULT 3.00 CHECK (icbf_rate BETWEEN 0 AND 100),
    severance_rate             numeric(5,2) NOT NULL DEFAULT 8.33 CHECK (severance_rate BETWEEN 0 AND 100),
    vacation_rate              numeric(5,2) NOT NULL DEFAULT 4.17 CHECK (vacation_rate BETWEEN 0 AND 100),
    severance_interest_rate    numeric(5,2) NOT NULL DEFAULT 1.00 CHECK (severance_interest_rate BETWEEN 0 AND 100),
    transport_allowance        numeric(14,2) NOT NULL DEFAULT 0.00 CHECK (transport_allowance >= 0),
    minimum_wage               numeric(14,2) NOT NULL DEFAULT 0.00 CHECK (minimum_wage >= 0),
    updated_by                 uuid,
    updated_at                 timestamptz NOT NULL DEFAULT now()
);

INSERT INTO payroll_settings (id) VALUES (1);

CREATE TABLE payroll_employees (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    document_type     varchar(20) NOT NULL DEFAULT 'CC',
    document_number   varchar(30) NOT NULL,
    full_name         varchar(200) NOT NULL,
    position          varchar(100) NOT NULL,
    base_salary       numeric(14,2) NOT NULL CHECK (base_salary >= 0),
    start_date        date NOT NULL,
    end_date          date,
    status            varchar(20) NOT NULL DEFAULT 'ACTIVE'
                      CHECK (status IN ('ACTIVE', 'INACTIVE')),
    bank_name         varchar(100),
    bank_account      varchar(50),
    cost_center       varchar(100),
    fixed_deductions  numeric(14,2) NOT NULL DEFAULT 0.00 CHECK (fixed_deductions >= 0),
    notes             text,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_payroll_employee_document UNIQUE (document_type, document_number),
    CONSTRAINT ck_payroll_employee_dates CHECK (end_date IS NULL OR end_date >= start_date)
);
CREATE INDEX ix_payroll_employees_status ON payroll_employees (status);
CREATE INDEX ix_payroll_employees_name ON payroll_employees (lower(full_name));

CREATE TABLE payroll_runs (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    run_number            varchar(30) NOT NULL UNIQUE,
    period                varchar(7) NOT NULL
                          CHECK (period ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    status                varchar(20) NOT NULL DEFAULT 'DRAFT'
                          CHECK (status IN ('DRAFT', 'CALCULATED', 'PAID', 'VOID')),
    total_gross           numeric(14,2) NOT NULL DEFAULT 0.00 CHECK (total_gross >= 0),
    total_deductions      numeric(14,2) NOT NULL DEFAULT 0.00 CHECK (total_deductions >= 0),
    total_net             numeric(14,2) NOT NULL DEFAULT 0.00 CHECK (total_net >= 0),
    total_employer_cost   numeric(14,2) NOT NULL DEFAULT 0.00 CHECK (total_employer_cost >= 0),
    notes                 text,
    created_by            uuid NOT NULL,
    created_at            timestamptz NOT NULL DEFAULT now(),
    calculated_at         timestamptz,
    paid_at               timestamptz,
    voided_at             timestamptz,
    void_reason           text
);
CREATE INDEX ix_payroll_runs_status ON payroll_runs (status);
CREATE INDEX ix_payroll_runs_period ON payroll_runs (period DESC);
CREATE UNIQUE INDEX ux_payroll_runs_period_active ON payroll_runs (period) WHERE status <> 'VOID';

CREATE TABLE payroll_items (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id                uuid NOT NULL REFERENCES payroll_runs(id) ON DELETE CASCADE,
    employee_id           uuid NOT NULL REFERENCES payroll_employees(id) ON DELETE RESTRICT,
    employee_name         varchar(200) NOT NULL,
    document_number       varchar(30) NOT NULL,
    position              varchar(100) NOT NULL,
    base_salary           numeric(14,2) NOT NULL CHECK (base_salary >= 0),
    worked_days           int NOT NULL CHECK (worked_days BETWEEN 1 AND 31),
    gross_pay             numeric(14,2) NOT NULL CHECK (gross_pay >= 0),
    health_deduction      numeric(14,2) NOT NULL DEFAULT 0.00 CHECK (health_deduction >= 0),
    pension_deduction     numeric(14,2) NOT NULL DEFAULT 0.00 CHECK (pension_deduction >= 0),
    other_deductions      numeric(14,2) NOT NULL DEFAULT 0.00 CHECK (other_deductions >= 0),
    net_pay               numeric(14,2) NOT NULL CHECK (net_pay >= 0),
    health_employer       numeric(14,2) NOT NULL DEFAULT 0.00 CHECK (health_employer >= 0),
    pension_employer      numeric(14,2) NOT NULL DEFAULT 0.00 CHECK (pension_employer >= 0),
    arl                   numeric(14,2) NOT NULL DEFAULT 0.00 CHECK (arl >= 0),
    compensation          numeric(14,2) NOT NULL DEFAULT 0.00 CHECK (compensation >= 0),
    sena                  numeric(14,2) NOT NULL DEFAULT 0.00 CHECK (sena >= 0),
    icbf                  numeric(14,2) NOT NULL DEFAULT 0.00 CHECK (icbf >= 0),
    severance             numeric(14,2) NOT NULL DEFAULT 0.00 CHECK (severance >= 0),
    vacation              numeric(14,2) NOT NULL DEFAULT 0.00 CHECK (vacation >= 0),
    severance_interest    numeric(14,2) NOT NULL DEFAULT 0.00 CHECK (severance_interest >= 0),
    employer_cost         numeric(14,2) NOT NULL DEFAULT 0.00 CHECK (employer_cost >= 0),
    created_at            timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_payroll_item_employee UNIQUE (run_id, employee_id)
);
CREATE INDEX ix_payroll_items_run ON payroll_items (run_id);
