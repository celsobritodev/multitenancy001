CREATE TABLE IF NOT EXISTS accounts (
    id BIGSERIAL PRIMARY KEY,

    name VARCHAR(150) NOT NULL,
    slug VARCHAR(100) NOT NULL UNIQUE,          -- 👈 IDENTIFICADOR DO TENANT
    schema_name VARCHAR(100) NOT NULL UNIQUE,   -- 👈 NOME DO SCHEMA

    status VARCHAR(50) DEFAULT 'FREE_TRIAL',

    created_at TIMESTAMP DEFAULT NOW(),
    trial_end_date TIMESTAMP,
    payment_due_date TIMESTAMP,
    next_billing_date TIMESTAMP,

    subscription_plan VARCHAR(50) DEFAULT 'FREE',

    max_users INTEGER DEFAULT 5,
    max_products INTEGER DEFAULT 100,
    max_storage_mb INTEGER DEFAULT 100,

    company_document VARCHAR(20),
    company_phone VARCHAR(20),
    company_email VARCHAR(150),
    company_address VARCHAR(500),
    company_city VARCHAR(100),
    company_state VARCHAR(50),
    company_country VARCHAR(50) DEFAULT 'Brasil',

    timezone VARCHAR(50) DEFAULT 'America/Sao_Paulo',
    locale VARCHAR(10) DEFAULT 'pt_BR',
    currency VARCHAR(3) DEFAULT 'BRL',

    settings_json TEXT,
    metadata_json TEXT,

    deleted_at TIMESTAMP,
    deleted BOOLEAN DEFAULT false,

    CONSTRAINT ux_accounts_name UNIQUE (name)
);
