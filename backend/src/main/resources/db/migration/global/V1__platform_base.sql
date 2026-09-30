-- Schema público: identidad, tenants, membresías, tokens y auditoría global.

CREATE TABLE administrator_companies (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name        varchar(200) NOT NULL,
    nit         varchar(30),
    status      varchar(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_companies_nit ON administrator_companies (nit) WHERE nit IS NOT NULL;

CREATE TABLE tenants (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id   uuid         NOT NULL REFERENCES administrator_companies (id),
    name         varchar(200) NOT NULL,
    nit          varchar(30),
    slug         varchar(40)  NOT NULL,
    schema_name  varchar(47)  NOT NULL,
    address      varchar(250),
    city         varchar(100),
    department   varchar(100),
    phone        varchar(40),
    email        varchar(254),
    currency     char(3)      NOT NULL DEFAULT 'COP',
    timezone     varchar(60)  NOT NULL DEFAULT 'America/Bogota',
    locale       varchar(10)  NOT NULL DEFAULT 'es-CO',
    status       varchar(20)  NOT NULL DEFAULT 'PROVISIONING'
                 CHECK (status IN ('PROVISIONING', 'ACTIVE', 'SUSPENDED', 'FAILED')),
    settings     jsonb        NOT NULL DEFAULT '{}'::jsonb,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ux_tenants_slug UNIQUE (slug),
    CONSTRAINT ux_tenants_schema UNIQUE (schema_name),
    CONSTRAINT ck_tenants_schema CHECK (schema_name ~ '^tenant_[a-z][a-z0-9_]{2,39}$')
);
CREATE INDEX ix_tenants_company ON tenants (company_id);

CREATE TABLE tenant_features (
    tenant_id  uuid        NOT NULL REFERENCES tenants (id),
    feature    varchar(50) NOT NULL,
    enabled    boolean     NOT NULL DEFAULT false,
    PRIMARY KEY (tenant_id, feature)
);

CREATE TABLE users (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email            varchar(254) NOT NULL,
    password_hash    varchar(100) NOT NULL,
    full_name        varchar(200) NOT NULL,
    platform_role    varchar(40) CHECK (platform_role IS NULL OR platform_role = 'SUPER_ADMIN_PLATFORM'),
    status           varchar(20)  NOT NULL DEFAULT 'INVITED' CHECK (status IN ('INVITED', 'ACTIVE', 'DISABLED')),
    failed_attempts  integer      NOT NULL DEFAULT 0,
    locked_until     timestamptz,
    last_login_at    timestamptz,
    last_tenant_id   uuid REFERENCES tenants (id),
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_users_email ON users (lower(email));

CREATE TABLE roles (
    code   varchar(40) PRIMARY KEY,
    name   varchar(100) NOT NULL,
    scope  varchar(10)  NOT NULL CHECK (scope IN ('PLATFORM', 'TENANT'))
);

CREATE TABLE permissions (
    code         varchar(60) PRIMARY KEY,
    description  varchar(200) NOT NULL
);

CREATE TABLE role_permissions (
    role_code        varchar(40) NOT NULL REFERENCES roles (code),
    permission_code  varchar(60) NOT NULL REFERENCES permissions (code),
    PRIMARY KEY (role_code, permission_code)
);

CREATE TABLE tenant_memberships (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           uuid        NOT NULL REFERENCES users (id),
    tenant_id         uuid        NOT NULL REFERENCES tenants (id),
    role_code         varchar(40) NOT NULL REFERENCES roles (code),
    status            varchar(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REVOKED')),
    granted_at        timestamptz NOT NULL DEFAULT now(),
    granted_by        uuid REFERENCES users (id),
    revoked_at        timestamptz,
    revoked_by        uuid REFERENCES users (id),
    last_activity_at  timestamptz,
    updated_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_membership UNIQUE (user_id, tenant_id)
);
CREATE INDEX ix_memberships_tenant ON tenant_memberships (tenant_id, status);

-- Historial inmutable de accesos: asignación, activación, suspensión, revocación, ingreso y salida.
CREATE TABLE tenant_access_history (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        uuid        NOT NULL REFERENCES users (id),
    tenant_id      uuid        NOT NULL REFERENCES tenants (id),
    event          varchar(20) NOT NULL
                   CHECK (event IN ('GRANTED', 'ROLE_CHANGED', 'SUSPENDED', 'ACTIVATED', 'REVOKED', 'ENTERED', 'LEFT')),
    role_code      varchar(40),
    actor_user_id  uuid REFERENCES users (id),
    occurred_at    timestamptz NOT NULL DEFAULT now(),
    ip             varchar(64)
);
CREATE INDEX ix_access_history ON tenant_access_history (user_id, tenant_id, occurred_at DESC);

CREATE TABLE refresh_tokens (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      uuid        NOT NULL REFERENCES users (id),
    token_hash   varchar(64) NOT NULL,
    expires_at   timestamptz NOT NULL,
    revoked_at   timestamptz,
    replaced_by  uuid,
    created_at   timestamptz NOT NULL DEFAULT now(),
    ip           varchar(64),
    user_agent   varchar(300),
    CONSTRAINT ux_refresh_hash UNIQUE (token_hash)
);
CREATE INDEX ix_refresh_user ON refresh_tokens (user_id) WHERE revoked_at IS NULL;

CREATE TABLE one_time_tokens (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid        NOT NULL REFERENCES users (id),
    purpose     varchar(20) NOT NULL CHECK (purpose IN ('PASSWORD_RESET', 'INVITATION')),
    token_hash  varchar(64) NOT NULL,
    expires_at  timestamptz NOT NULL,
    used_at     timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_ott_hash UNIQUE (token_hash)
);

CREATE TABLE audit_logs (
    id             uuid PRIMARY KEY,
    occurred_at    timestamptz NOT NULL,
    tenant_id      uuid,
    actor_user_id  uuid,
    actor_email    varchar(254),
    action         varchar(60) NOT NULL,
    entity         varchar(60),
    entity_id      varchar(64),
    result         varchar(10) NOT NULL CHECK (result IN ('SUCCESS', 'FAILURE')),
    ip             varchar(64),
    user_agent     varchar(300),
    trace_id       varchar(64),
    details        jsonb NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX ix_audit_tenant_time ON audit_logs (tenant_id, occurred_at DESC);
CREATE INDEX ix_audit_action ON audit_logs (action, occurred_at DESC);

-- La auditoría es append-only: no se puede modificar ni borrar silenciosamente.
CREATE FUNCTION audit_logs_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs es de solo inserción';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_logs_immutable
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION audit_logs_immutable();

CREATE TRIGGER trg_access_history_immutable
    BEFORE UPDATE OR DELETE ON tenant_access_history
    FOR EACH ROW EXECUTE FUNCTION audit_logs_immutable();
