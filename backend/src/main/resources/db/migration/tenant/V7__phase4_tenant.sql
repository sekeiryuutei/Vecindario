-- Migración tenant V7: Zonas comunes y Reservas, PQRS y Convivencia, Comunicados, Notificaciones, Documentos, Asambleas

-- 1. ZONAS COMUNES Y RESERVAS
CREATE TABLE common_areas (
    id                               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name                             varchar(100) NOT NULL,
    description                      text,
    capacity                         int NOT NULL DEFAULT 10 CHECK (capacity > 0),
    requires_payment                 boolean NOT NULL DEFAULT false,
    price_per_slot                   numeric(14,2) NOT NULL DEFAULT 0.00 CHECK (price_per_slot >= 0),
    deposit_amount                   numeric(14,2) NOT NULL DEFAULT 0.00 CHECK (deposit_amount >= 0),
    requires_approval                boolean NOT NULL DEFAULT false,
    opening_time                     time NOT NULL DEFAULT '06:00:00',
    closing_time                     time NOT NULL DEFAULT '22:00:00',
    slot_duration_minutes            int NOT NULL DEFAULT 60 CHECK (slot_duration_minutes > 0),
    max_slots_per_reservation        int NOT NULL DEFAULT 4 CHECK (max_slots_per_reservation > 0),
    max_active_reservations_per_unit int NOT NULL DEFAULT 2 CHECK (max_active_reservations_per_unit > 0),
    min_advance_hours                int NOT NULL DEFAULT 12 CHECK (min_advance_hours >= 0),
    max_advance_days                 int NOT NULL DEFAULT 30 CHECK (max_advance_days > 0),
    cancellation_deadline_hours      int NOT NULL DEFAULT 24 CHECK (cancellation_deadline_hours >= 0),
    is_active                        boolean NOT NULL DEFAULT true,
    created_at                       timestamptz NOT NULL DEFAULT now(),
    updated_at                       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_common_area_times CHECK (opening_time < closing_time)
);

CREATE TABLE reservations (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    common_area_id      uuid NOT NULL REFERENCES common_areas(id),
    unit_id             uuid NOT NULL REFERENCES property_units(id),
    person_id           uuid REFERENCES people(id),
    created_by          uuid NOT NULL,
    start_time          timestamptz NOT NULL,
    end_time            timestamptz NOT NULL,
    status              varchar(20) NOT NULL DEFAULT 'APPROVED'
                        CHECK (status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'CANCELLED', 'COMPLETED')),
    total_price         numeric(14,2) NOT NULL DEFAULT 0.00 CHECK (total_price >= 0),
    deposit_amount      numeric(14,2) NOT NULL DEFAULT 0.00 CHECK (deposit_amount >= 0),
    payment_method      varchar(20) NOT NULL DEFAULT 'NONE'
                        CHECK (payment_method IN ('NONE', 'CASH', 'WOMPI', 'BANK_TRANSFER')),
    payment_reference   varchar(100),
    rejection_reason    varchar(500),
    cancellation_reason varchar(500),
    notes               varchar(500),
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_reservation_times CHECK (start_time < end_time)
);
CREATE INDEX ix_reservations_area_time ON reservations (common_area_id, start_time, end_time);
CREATE INDEX ix_reservations_unit ON reservations (unit_id, status);
CREATE INDEX ix_reservations_status ON reservations (status);

-- 2. PQRS Y CONVIVENCIA
CREATE SEQUENCE IF NOT EXISTS pqr_ticket_seq START WITH 1001;

CREATE TABLE pqrs (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_number     varchar(30) NOT NULL UNIQUE,
    unit_id           uuid REFERENCES property_units(id),
    person_id         uuid REFERENCES people(id),
    user_id           uuid NOT NULL,
    category          varchar(30) NOT NULL
                      CHECK (category IN ('PETICION', 'QUEJA', 'RECLAMO', 'SUGERENCIA', 'CONVIVENCIA_RUIDO', 'CONVIVENCIA_MASCOTAS', 'CONVIVENCIA_GENERAL', 'OTRO')),
    priority          varchar(20) NOT NULL DEFAULT 'MEDIUM'
                      CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    status            varchar(20) NOT NULL DEFAULT 'OPEN'
                      CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'REJECTED')),
    title             varchar(200) NOT NULL,
    description       text NOT NULL,
    is_anonymous      boolean NOT NULL DEFAULT false,
    assigned_to       uuid,
    sla_target_hours  int NOT NULL DEFAULT 72 CHECK (sla_target_hours > 0),
    sla_due_at        timestamptz NOT NULL,
    resolved_at       timestamptz,
    closed_at         timestamptz,
    resolution_notes  text,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_pqrs_status_due ON pqrs (status, sla_due_at);
CREATE INDEX ix_pqrs_unit ON pqrs (unit_id);
CREATE INDEX ix_pqrs_category ON pqrs (category);

CREATE TABLE pqrs_messages (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    pqr_id          uuid NOT NULL REFERENCES pqrs(id) ON DELETE CASCADE,
    sender_user_id  uuid NOT NULL,
    sender_name     varchar(150) NOT NULL,
    is_internal     boolean NOT NULL DEFAULT false,
    message         text NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_pqrs_messages_pqr ON pqrs_messages (pqr_id, created_at ASC);

-- 3. COMUNICADOS Y AVISOS
CREATE TABLE communications (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    title            varchar(200) NOT NULL,
    summary          varchar(500),
    content          text NOT NULL,
    priority         varchar(20) NOT NULL DEFAULT 'NORMAL'
                     CHECK (priority IN ('NORMAL', 'IMPORTANT', 'URGENT')),
    target_audience  varchar(20) NOT NULL DEFAULT 'ALL'
                     CHECK (target_audience IN ('ALL', 'OWNERS', 'TENANTS', 'RESIDENTS', 'COUNCIL')),
    target_tower     varchar(50),
    status           varchar(20) NOT NULL DEFAULT 'PUBLISHED'
                     CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    is_pinned        boolean NOT NULL DEFAULT false,
    created_by       uuid NOT NULL,
    published_at     timestamptz NOT NULL DEFAULT now(),
    expires_at       timestamptz,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_communications_pub ON communications (status, published_at DESC);

CREATE TABLE communication_reads (
    communication_id uuid NOT NULL REFERENCES communications(id) ON DELETE CASCADE,
    user_id          uuid NOT NULL,
    read_at          timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (communication_id, user_id)
);

-- 4. CONFIGURACIÓN Y LOGS DE NOTIFICACIONES
CREATE TABLE tenant_notification_settings (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email_enabled         boolean NOT NULL DEFAULT true,
    push_enabled          boolean NOT NULL DEFAULT true,
    whatsapp_enabled      boolean NOT NULL DEFAULT false,
    sender_display_name   varchar(100) NOT NULL DEFAULT 'Vecindad PH',
    updated_at            timestamptz NOT NULL DEFAULT now()
);
INSERT INTO tenant_notification_settings (email_enabled, push_enabled, whatsapp_enabled, sender_display_name)
VALUES (true, true, false, 'Vecindad PH');

CREATE TABLE notification_logs (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_email  varchar(150),
    recipient_phone  varchar(50),
    user_id          uuid,
    channel          varchar(20) NOT NULL CHECK (channel IN ('EMAIL', 'PUSH', 'WHATSAPP')),
    type             varchar(50) NOT NULL,
    subject          varchar(250) NOT NULL,
    body             text NOT NULL,
    status           varchar(20) NOT NULL DEFAULT 'SENT' CHECK (status IN ('SENT', 'FAILED', 'SKIPPED')),
    error_detail     text,
    created_at       timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_notifications_user_date ON notification_logs (user_id, created_at DESC);

-- 5. DOCUMENTOS Y REPOSITORIO DIGITAL
CREATE TABLE documents (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    title         varchar(200) NOT NULL,
    description   varchar(500),
    category      varchar(40) NOT NULL
                  CHECK (category IN ('ACTA_ASAMBLEA', 'ESTADO_FINANCIERO', 'REGLAMENTO_PROPIEDAD_HORIZONTAL', 'PRESUPUESTO', 'CONTRATO', 'CIRCULAR', 'OTRO')),
    storage_key   varchar(500) NOT NULL,
    file_name     varchar(255) NOT NULL,
    file_size     bigint NOT NULL CHECK (file_size >= 0),
    content_type  varchar(100) NOT NULL,
    minimum_role  varchar(30) NOT NULL DEFAULT 'RESIDENTE'
                  CHECK (minimum_role IN ('RESIDENTE', 'PROPIETARIO', 'CONSEJO', 'ADMINISTRADOR')),
    uploaded_by   uuid NOT NULL,
    is_active     boolean NOT NULL DEFAULT true,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_documents_category ON documents (category, created_at DESC);

-- 6. ASAMBLEAS Y VOTACIÓN PONDERADA (LEY 675)
CREATE TABLE assemblies (
    id                          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    title                       varchar(200) NOT NULL,
    type                        varchar(20) NOT NULL DEFAULT 'ORDINARIA'
                                CHECK (type IN ('ORDINARIA', 'EXTRAORDINARIA')),
    scheduled_at                timestamptz NOT NULL,
    status                      varchar(20) NOT NULL DEFAULT 'SCHEDULED'
                                CHECK (status IN ('SCHEDULED', 'IN_PROGRESS', 'ADJOURNED', 'CLOSED')),
    location                    varchar(200),
    is_virtual                  boolean NOT NULL DEFAULT false,
    meeting_link                varchar(500),
    required_quorum_percentage  numeric(5,2) NOT NULL DEFAULT 51.00 CHECK (required_quorum_percentage > 0),
    created_at                  timestamptz NOT NULL DEFAULT now(),
    updated_at                  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE assembly_attendances (
    assembly_id    uuid NOT NULL REFERENCES assemblies(id) ON DELETE CASCADE,
    unit_id        uuid NOT NULL REFERENCES property_units(id),
    person_id      uuid REFERENCES people(id),
    is_proxy       boolean NOT NULL DEFAULT false,
    proxy_name     varchar(150),
    checked_in_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (assembly_id, unit_id)
);

CREATE TABLE assembly_topics (
    id                           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    assembly_id                  uuid NOT NULL REFERENCES assemblies(id) ON DELETE CASCADE,
    order_num                    int NOT NULL DEFAULT 1,
    title                        varchar(200) NOT NULL,
    description                  text,
    voting_type                  varchar(20) NOT NULL DEFAULT 'COEFFICIENT'
                                 CHECK (voting_type IN ('NOMINAL', 'COEFFICIENT')),
    status                       varchar(20) NOT NULL DEFAULT 'PENDING'
                                 CHECK (status IN ('PENDING', 'VOTING_OPEN', 'VOTING_CLOSED')),
    requires_qualified_majority  boolean NOT NULL DEFAULT false,
    options                      jsonb NOT NULL DEFAULT '["A FAVOR", "EN CONTRA", "ABSTENCION"]'::jsonb,
    created_at                   timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE assembly_votes (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    topic_id    uuid NOT NULL REFERENCES assembly_topics(id) ON DELETE CASCADE,
    unit_id     uuid NOT NULL REFERENCES property_units(id),
    person_id   uuid REFERENCES people(id),
    choice      varchar(100) NOT NULL,
    weight      numeric(9,6) NOT NULL DEFAULT 1.000000 CHECK (weight > 0),
    voted_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_assembly_votes_topic_unit UNIQUE (topic_id, unit_id)
);
CREATE INDEX ix_assembly_votes_topic ON assembly_votes (topic_id);

