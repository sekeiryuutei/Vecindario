-- Migración tenant V8: Proveedores, Mantenimiento y Órdenes de Trabajo, Presupuesto y Ejecución

-- 1. PROVEEDORES Y CONTRATISTAS
CREATE TABLE suppliers (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tax_id        varchar(30) NOT NULL UNIQUE,
    name          varchar(150) NOT NULL,
    contact_name  varchar(150),
    contact_phone varchar(30),
    contact_email varchar(100),
    category      varchar(50) NOT NULL,
    rating        int CHECK (rating IS NULL OR (rating >= 1 AND rating <= 5)),
    is_active     boolean NOT NULL DEFAULT true,
    notes         text,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_suppliers_category ON suppliers (category);
CREATE INDEX ix_suppliers_active ON suppliers (is_active);

CREATE TABLE supplier_contracts (
    id                       uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_id              uuid NOT NULL REFERENCES suppliers(id) ON DELETE CASCADE,
    contract_number          varchar(50) NOT NULL,
    title                    varchar(200) NOT NULL,
    description              text,
    start_date               date NOT NULL,
    end_date                 date NOT NULL,
    monthly_amount           numeric(14,2) NOT NULL DEFAULT 0.00 CHECK (monthly_amount >= 0),
    total_amount             numeric(14,2) NOT NULL DEFAULT 0.00 CHECK (total_amount >= 0),
    has_insurance_policy     boolean NOT NULL DEFAULT false,
    insurance_policy_number  varchar(100),
    insurance_expiration_date date,
    status                   varchar(20) NOT NULL DEFAULT 'ACTIVE'
                             CHECK (status IN ('ACTIVE', 'EXPIRED', 'TERMINATED', 'CANCELLED')),
    created_at               timestamptz NOT NULL DEFAULT now(),
    updated_at               timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_contract_dates CHECK (start_date <= end_date)
);
CREATE INDEX ix_contracts_supplier ON supplier_contracts (supplier_id);
CREATE INDEX ix_contracts_status ON supplier_contracts (status);

-- 2. ACTIVOS Y PLANES DE MANTENIMIENTO
CREATE TABLE maintenance_assets (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name                varchar(150) NOT NULL,
    category            varchar(50) NOT NULL,
    location            varchar(150),
    serial_number       varchar(100),
    brand_model         varchar(150),
    installation_date   date,
    warranty_expiration date,
    status              varchar(20) NOT NULL DEFAULT 'OPERATIONAL'
                        CHECK (status IN ('OPERATIONAL', 'DEGRADED', 'OUT_OF_SERVICE', 'RETIRED')),
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_maintenance_assets_status ON maintenance_assets (status);
CREATE INDEX ix_maintenance_assets_cat ON maintenance_assets (category);

CREATE TABLE maintenance_plans (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_id             uuid REFERENCES maintenance_assets(id) ON DELETE SET NULL,
    title                varchar(200) NOT NULL,
    description          text,
    frequency_days       int NOT NULL DEFAULT 30 CHECK (frequency_days > 0),
    last_performed_at    date,
    next_due_date        date NOT NULL,
    assigned_supplier_id uuid REFERENCES suppliers(id) ON DELETE SET NULL,
    is_active            boolean NOT NULL DEFAULT true,
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_plans_next_due ON maintenance_plans (next_due_date) WHERE is_active = true;

-- 3. ÓRDENES DE TRABAJO
CREATE SEQUENCE IF NOT EXISTS work_order_seq START WITH 1001;

CREATE TABLE work_orders (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    order_number     varchar(30) NOT NULL UNIQUE,
    asset_id         uuid REFERENCES maintenance_assets(id) ON DELETE SET NULL,
    plan_id          uuid REFERENCES maintenance_plans(id) ON DELETE SET NULL,
    supplier_id      uuid REFERENCES suppliers(id) ON DELETE SET NULL,
    type             varchar(20) NOT NULL DEFAULT 'CORRECTIVE'
                     CHECK (type IN ('PREVENTIVE', 'CORRECTIVE', 'EMERGENCY', 'IMPROVEMENT')),
    title            varchar(200) NOT NULL,
    description      text NOT NULL,
    priority         varchar(20) NOT NULL DEFAULT 'MEDIUM'
                     CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    status           varchar(20) NOT NULL DEFAULT 'PENDING'
                     CHECK (status IN ('PENDING', 'ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    estimated_cost   numeric(14,2) NOT NULL DEFAULT 0.00 CHECK (estimated_cost >= 0),
    actual_cost      numeric(14,2) NOT NULL DEFAULT 0.00 CHECK (actual_cost >= 0),
    created_by       uuid NOT NULL,
    assigned_to      varchar(150),
    scheduled_start  timestamptz,
    scheduled_end    timestamptz,
    started_at       timestamptz,
    completed_at     timestamptz,
    completion_notes text,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_work_orders_status ON work_orders (status);
CREATE INDEX ix_work_orders_asset ON work_orders (asset_id);
CREATE INDEX ix_work_orders_supplier ON work_orders (supplier_id);

-- 4. PRESUPUESTO Y CONTABILIDAD ADMINISTRATIVA
CREATE TABLE annual_budgets (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    year                  int NOT NULL UNIQUE CHECK (year >= 2020 AND year <= 2100),
    title                 varchar(150) NOT NULL,
    status                varchar(20) NOT NULL DEFAULT 'APPROVED'
                          CHECK (status IN ('DRAFT', 'APPROVED', 'CLOSED')),
    total_income_budgeted numeric(14,2) NOT NULL DEFAULT 0.00 CHECK (total_income_budgeted >= 0),
    total_expense_budgeted numeric(14,2) NOT NULL DEFAULT 0.00 CHECK (total_expense_budgeted >= 0),
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE budget_items (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    budget_id       uuid NOT NULL REFERENCES annual_budgets(id) ON DELETE CASCADE,
    code            varchar(30) NOT NULL,
    name            varchar(150) NOT NULL,
    type            varchar(10) NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    budgeted_amount numeric(14,2) NOT NULL DEFAULT 0.00 CHECK (budgeted_amount >= 0),
    executed_amount numeric(14,2) NOT NULL DEFAULT 0.00 CHECK (executed_amount >= 0),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_budget_item_code UNIQUE (budget_id, code)
);
CREATE INDEX ix_budget_items_budget ON budget_items (budget_id);

CREATE TABLE budget_executions (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    budget_item_id  uuid NOT NULL REFERENCES budget_items(id) ON DELETE RESTRICT,
    work_order_id   uuid REFERENCES work_orders(id) ON DELETE SET NULL,
    supplier_id     uuid REFERENCES suppliers(id) ON DELETE SET NULL,
    amount          numeric(14,2) NOT NULL CHECK (amount > 0),
    execution_date  date NOT NULL DEFAULT CURRENT_DATE,
    invoice_number  varchar(50),
    description     text NOT NULL,
    registered_by   uuid NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_budget_executions_item ON budget_executions (budget_item_id);
CREATE INDEX ix_budget_executions_date ON budget_executions (execution_date);

