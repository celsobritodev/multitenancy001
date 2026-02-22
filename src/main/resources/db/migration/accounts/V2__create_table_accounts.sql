-- V2__create_table_accounts.sql
SET search_path TO public;

CREATE TABLE IF NOT EXISTS accounts (
    id BIGSERIAL PRIMARY KEY,

    -- Removido o DEFAULT e o CHECK será gerenciado pela aplicação
    account_type   VARCHAR(20) NOT NULL,
    -- Removido o DEFAULT e o CHECK será gerenciado pela aplicação
    account_origin VARCHAR(20) NOT NULL,

    display_name VARCHAR(150) NOT NULL,
    legal_name   VARCHAR(200),
    legal_entity_type VARCHAR(20) NOT NULL,

    tenant_schema VARCHAR(100) NOT NULL,
    slug        VARCHAR(80)  NOT NULL,

    status            VARCHAR(50) NOT NULL,
    subscription_plan VARCHAR(50) NOT NULL,

    -- emails case-insensitive
    login_email   CITEXT NOT NULL,
    billing_email CITEXT,

    tax_id_type      VARCHAR(20),
    tax_id_number    VARCHAR(40),
    tax_country_code VARCHAR(2)  NOT NULL DEFAULT 'BR',

    phone   VARCHAR(20),
    address VARCHAR(500),
    city    VARCHAR(100),
    state   VARCHAR(50),
    country VARCHAR(60) NOT NULL DEFAULT 'Brasil',

    timezone VARCHAR(60) NOT NULL DEFAULT 'America/Sao_Paulo',
    locale   VARCHAR(20) NOT NULL DEFAULT 'pt_BR',
    currency VARCHAR(3)  NOT NULL DEFAULT 'BRL',

    -- auditoria (instantes reais)
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    created_by BIGINT,
    updated_by BIGINT,
    deleted_by BIGINT,

    created_by_email CITEXT,
    updated_by_email CITEXT,
    deleted_by_email CITEXT,

    trial_end_at    TIMESTAMPTZ,
    payment_due_date  DATE,
    next_billing_date DATE,

    settings_json TEXT,
    metadata_json TEXT,

    deleted    BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMPTZ
);

-- =========================
-- Índices/Uniqueness (soft-delete aware)
-- =========================
CREATE UNIQUE INDEX IF NOT EXISTS ux_accounts_tenant_schema_active
    ON accounts (tenant_schema)
    WHERE deleted = false;

CREATE UNIQUE INDEX IF NOT EXISTS ux_accounts_slug_active
    ON accounts (slug)
    WHERE deleted = false;

CREATE UNIQUE INDEX IF NOT EXISTS ux_accounts_login_email_active
    ON accounts (login_email)
    WHERE deleted = false;

-- tax_id pode ser nulo (permitir múltiplos nulos)
CREATE UNIQUE INDEX IF NOT EXISTS ux_accounts_tax_id_active
    ON accounts (tax_id_type, tax_id_number, tax_country_code)
    WHERE deleted = false AND tax_id_number IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_accounts_status ON accounts (status);
CREATE INDEX IF NOT EXISTS idx_accounts_deleted ON accounts (deleted);
CREATE INDEX IF NOT EXISTS idx_accounts_created_at ON accounts (created_at);
CREATE INDEX IF NOT EXISTS idx_accounts_payment_due_date ON accounts (payment_due_date) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_accounts_trial_end_at ON accounts (trial_end_at) WHERE deleted = false;