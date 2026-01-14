-- V1__create_table_accounts.sql
SET search_path TO public;

CREATE TABLE IF NOT EXISTS accounts (
    id BIGSERIAL PRIMARY KEY,

    account_type VARCHAR(20) NOT NULL DEFAULT 'TENANT',

    name VARCHAR(150) NOT NULL,

    schema_name VARCHAR(100) NOT NULL,
    slug VARCHAR(50) NOT NULL,

    status VARCHAR(50) NOT NULL DEFAULT 'FREE_TRIAL',
    subscription_plan VARCHAR(50) NOT NULL DEFAULT 'FREE',

    company_doc_type VARCHAR(10) NOT NULL,
    company_doc_number VARCHAR(20) NOT NULL,
    company_email VARCHAR(150) NOT NULL,

    company_phone VARCHAR(20),
    company_address VARCHAR(500),
    company_city VARCHAR(100),
    company_state VARCHAR(50),
    company_country VARCHAR(60),

    timezone VARCHAR(60),
    locale VARCHAR(20),
    currency VARCHAR(10),

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,

    trial_end_date TIMESTAMP,
    payment_due_date TIMESTAMP,
    next_billing_date TIMESTAMP,

    settings_json TEXT,
    metadata_json TEXT,

    deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP
);

-- Opcional (recomendado): garante valores válidos do enum no banco
ALTER TABLE accounts
    ADD CONSTRAINT chk_accounts_account_type
    CHECK (account_type IN ('TENANT', 'SYSTEM'));

-- Unicidade de documento por conta ativa
CREATE UNIQUE INDEX IF NOT EXISTS ux_accounts_company_doc_active
ON accounts (company_doc_type, company_doc_number)
WHERE deleted = false;

-- Unicidade de email por conta ativa
CREATE UNIQUE INDEX IF NOT EXISTS ux_accounts_company_email_active
ON accounts (company_email)
WHERE deleted = false;

-- Unicidade de schema (global)
CREATE UNIQUE INDEX IF NOT EXISTS uk_accounts_schema_name
ON accounts (schema_name);

-- Unicidade de slug (global)
CREATE UNIQUE INDEX IF NOT EXISTS uk_accounts_slug
ON accounts (slug);
