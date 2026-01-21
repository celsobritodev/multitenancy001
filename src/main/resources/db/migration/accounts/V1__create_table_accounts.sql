-- V1__create_table_accounts.sql
SET search_path TO public;

CREATE TABLE IF NOT EXISTS accounts (
    id BIGSERIAL PRIMARY KEY,

    account_type   VARCHAR(20) NOT NULL DEFAULT 'TENANT',
    account_origin VARCHAR(20) NOT NULL DEFAULT 'ADMIN',

    -- Core identity (neutro)
    display_name VARCHAR(150) NOT NULL,
    legal_name   VARCHAR(200),
    legal_entity_type VARCHAR(20) NOT NULL DEFAULT 'COMPANY', -- INDIVIDUAL | COMPANY

    schema_name VARCHAR(100) NOT NULL,
    slug        VARCHAR(80)  NOT NULL,

    status            VARCHAR(50) NOT NULL DEFAULT 'FREE_TRIAL',
    subscription_plan VARCHAR(50) NOT NULL DEFAULT 'FREE',

    -- Emails (neutro)
    login_email   VARCHAR(150) NOT NULL,
    billing_email VARCHAR(150),

    -- Documento fiscal/identificador legal (neutro)
    tax_id_type      VARCHAR(20),
    tax_id_number    VARCHAR(40),
    tax_country_code VARCHAR(2)  NOT NULL DEFAULT 'BR',

    -- Contato / Endereço (neutro)
    phone   VARCHAR(20),
    address VARCHAR(500),
    city    VARCHAR(100),
    state   VARCHAR(50),
    country VARCHAR(60) NOT NULL DEFAULT 'Brasil',

    -- Preferências
    timezone VARCHAR(60) NOT NULL DEFAULT 'America/Sao_Paulo',
    locale   VARCHAR(20) NOT NULL DEFAULT 'pt_BR',
    currency VARCHAR(10) NOT NULL DEFAULT 'BRL',

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,

    trial_end_date   TIMESTAMP,
    payment_due_date TIMESTAMP,
    next_billing_date TIMESTAMP,

    settings_json TEXT,
    metadata_json TEXT,

    deleted   BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP
);

-- =========================
-- CHECK constraints (enums)
-- =========================
ALTER TABLE accounts
    ADD CONSTRAINT chk_accounts_account_type
    CHECK (account_type IN ('TENANT', 'PLATFORM'));

ALTER TABLE accounts
    ADD CONSTRAINT chk_accounts_account_origin
    CHECK (account_origin IN ('BUILT_IN', 'ADMIN', 'API'));

ALTER TABLE accounts
    ADD CONSTRAINT chk_accounts_subscription_plan
    CHECK (subscription_plan IN ('FREE', 'PRO', 'ENTERPRISE', 'BUILT_IN_PLAN'));

ALTER TABLE accounts
    ADD CONSTRAINT chk_accounts_legal_entity_type
    CHECK (legal_entity_type IN ('INDIVIDUAL', 'COMPANY'));

-- tax_id_type: hoje você usa CPF/CNPJ, mas deixei neutro para crescer.
-- se quiser travar estrito em CPF/CNPJ por enquanto, use a versão "estreita" abaixo.
ALTER TABLE accounts
    ADD CONSTRAINT chk_accounts_tax_id_type
    CHECK (tax_id_type IS NULL OR tax_id_type IN ('CPF', 'CNPJ'));

-- Coerência: ou preenche ambos ou nenhum
ALTER TABLE accounts
    ADD CONSTRAINT chk_accounts_tax_id_pair
    CHECK (
        (tax_id_type IS NULL AND tax_id_number IS NULL)
        OR
        (tax_id_type IS NOT NULL AND tax_id_number IS NOT NULL)
    );

-- =========================
-- Índices / Uniques
-- =========================

-- Só 1 PLATFORM no banco (seu requisito)
CREATE UNIQUE INDEX IF NOT EXISTS ux_accounts_single_platform
ON accounts (account_type)
WHERE account_type = 'PLATFORM';

-- Unicidade de documento por conta ativa (neutro)
CREATE UNIQUE INDEX IF NOT EXISTS ux_accounts_tax_id_active
ON accounts (tax_country_code, tax_id_type, tax_id_number)
WHERE deleted = false AND tax_id_type IS NOT NULL AND tax_id_number IS NOT NULL;

-- Unicidade de email principal por conta ativa (sua regra atual)
CREATE UNIQUE INDEX IF NOT EXISTS ux_accounts_login_email_active
ON accounts (login_email)
WHERE deleted = false;

-- schema e slug globais
CREATE UNIQUE INDEX IF NOT EXISTS uk_accounts_schema_name
ON accounts (schema_name);

CREATE UNIQUE INDEX IF NOT EXISTS uk_accounts_slug
ON accounts (slug);

CREATE UNIQUE INDEX IF NOT EXISTS ux_accounts_billing_email_active
ON accounts (billing_email)
WHERE deleted = false AND billing_email IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_accounts_display_name
ON accounts (display_name);



