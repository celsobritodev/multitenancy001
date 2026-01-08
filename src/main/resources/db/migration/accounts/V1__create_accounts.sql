-- V1__create_accounts.sql
SET search_path TO public;

CREATE TABLE IF NOT EXISTS accounts (
    id BIGSERIAL PRIMARY KEY,

    -- Flag para identificar contas do sistema
    is_system_account BOOLEAN NOT NULL DEFAULT false,

    name VARCHAR(150) NOT NULL,

    -- Infra
    schema_name VARCHAR(100) NOT NULL,
    slug VARCHAR(50) NOT NULL,

    -- Status / Plano
    status VARCHAR(50) NOT NULL,
    subscription_plan VARCHAR(50) DEFAULT 'FREE',

    max_users INTEGER DEFAULT 5,
    max_products INTEGER DEFAULT 100,
    max_storage_mb INTEGER DEFAULT 100,

    -- Identidade da empresa (CRÍTICO)
    company_doc_type VARCHAR(10) NOT NULL,
    company_doc_number VARCHAR(20) NOT NULL,
    company_email VARCHAR(150) NOT NULL,

    company_phone VARCHAR(20),
    company_address VARCHAR(500),
    company_city VARCHAR(100),
    company_state VARCHAR(50),
    company_country VARCHAR(50) DEFAULT 'Brasil',

    -- Localização
    timezone VARCHAR(50) DEFAULT 'America/Sao_Paulo',
    locale VARCHAR(10) DEFAULT 'pt_BR',
    currency VARCHAR(3) DEFAULT 'BRL',

    -- Datas
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    trial_end_date TIMESTAMP,
    payment_due_date TIMESTAMP,
    next_billing_date TIMESTAMP,

    settings_json TEXT,
    metadata_json TEXT,

    deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP
);

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

