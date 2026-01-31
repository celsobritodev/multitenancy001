-- V2__create_table_accounts.sql
SET search_path TO public;

CREATE TABLE IF NOT EXISTS accounts (
    id BIGSERIAL PRIMARY KEY,

    account_type   VARCHAR(20) NOT NULL DEFAULT 'TENANT',
    account_origin VARCHAR(20) NOT NULL DEFAULT 'ADMIN',

    display_name VARCHAR(150) NOT NULL,
    legal_name   VARCHAR(200),
    legal_entity_type VARCHAR(20) NOT NULL DEFAULT 'COMPANY', -- INDIVIDUAL | COMPANY

    schema_name VARCHAR(100) NOT NULL,
    slug        VARCHAR(80)  NOT NULL,

    status            VARCHAR(50) NOT NULL DEFAULT 'FREE_TRIAL',
    subscription_plan VARCHAR(50) NOT NULL DEFAULT 'FREE',

    -- ✅ emails case-insensitive
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

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,

    created_by BIGINT,
    updated_by BIGINT,
    deleted_by BIGINT,

    created_by_email CITEXT,
    updated_by_email CITEXT,
    deleted_by_email CITEXT,

    trial_end_date    TIMESTAMPTZ,
    payment_due_date  TIMESTAMPTZ,
    next_billing_date TIMESTAMPTZ,

    settings_json TEXT,
    metadata_json TEXT,

    deleted    BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMPTZ
);

-- =========================
-- CHECK constraints (idempotente)
-- =========================
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_accounts_account_type') THEN
        ALTER TABLE accounts
            ADD CONSTRAINT chk_accounts_account_type
            CHECK (account_type IN ('TENANT', 'PLATFORM'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_accounts_account_origin') THEN
        ALTER TABLE accounts
            ADD CONSTRAINT chk_accounts_account_origin
            CHECK (account_origin IN ('BUILT_IN', 'ADMIN', 'API'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_accounts_subscription_plan') THEN
        ALTER TABLE accounts
            ADD CONSTRAINT chk_accounts_subscription_plan
            CHECK (subscription_plan IN ('FREE', 'PRO', 'ENTERPRISE', 'BUILT_IN_PLAN'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_accounts_legal_entity_type') THEN
        ALTER TABLE accounts
            ADD CONSTRAINT chk_accounts_legal_entity_type
            CHECK (legal_entity_type IN ('INDIVIDUAL', 'COMPANY'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_accounts_tax_id_type') THEN
        ALTER TABLE accounts
            ADD CONSTRAINT chk_accounts_tax_id_type
            CHECK (tax_id_type IS NULL OR tax_id_type IN ('CPF', 'CNPJ'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_accounts_tax_id_pair') THEN
        ALTER TABLE accounts
            ADD CONSTRAINT chk_accounts_tax_id_pair
            CHECK (
                (tax_id_type IS NULL AND tax_id_number IS NULL)
                OR
                (tax_id_type IS NOT NULL AND tax_id_number IS NOT NULL)
            );
    END IF;
END $$;

-- =========================
-- Indexes / Uniques
-- (padronizado com os nomes que você queria no V10)
-- =========================

-- single PLATFORM
CREATE UNIQUE INDEX IF NOT EXISTS ux_accounts_single_platform
    ON accounts (account_type)
    WHERE account_type = 'PLATFORM';

-- TaxId único por país + tipo + número (soft-delete), ignorando NULL
CREATE UNIQUE INDEX IF NOT EXISTS uk_accounts_taxid_country_type_number_not_deleted
    ON accounts (tax_country_code, tax_id_type, tax_id_number)
    WHERE deleted = false
      AND tax_id_type IS NOT NULL
      AND tax_id_number IS NOT NULL;

-- login_email único (case-insensitive por ser CITEXT) respeitando soft-delete
CREATE UNIQUE INDEX IF NOT EXISTS uk_accounts_login_email_not_deleted
    ON accounts (login_email)
    WHERE deleted = false;

-- schema/slug globais (você pode manter assim; se quiser soft-delete também, me fala)
CREATE UNIQUE INDEX IF NOT EXISTS uk_accounts_schema_name
    ON accounts (schema_name);

CREATE UNIQUE INDEX IF NOT EXISTS uk_accounts_slug
    ON accounts (slug);

-- billing_email único quando preenchido (case-insensitive)
CREATE UNIQUE INDEX IF NOT EXISTS ux_accounts_billing_email_active
    ON accounts (billing_email)
    WHERE deleted = false AND billing_email IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_accounts_display_name
    ON accounts (display_name);
