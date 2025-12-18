SET search_path TO public;

CREATE TABLE accounts (
    id BIGSERIAL PRIMARY KEY,

    name VARCHAR(150) NOT NULL,

    -- Infra
    schema_name VARCHAR(100) NOT NULL UNIQUE,
    slug VARCHAR(50) NOT NULL UNIQUE,

    -- Status / Plano
    status VARCHAR(50) NOT NULL,
    subscription_plan VARCHAR(50) DEFAULT 'FREE',

    max_users INTEGER DEFAULT 5,
    max_products INTEGER DEFAULT 100,
    max_storage_mb INTEGER DEFAULT 100,

    -- Identidade da empresa (🔑 CRÍTICO)
    company_document VARCHAR(20) NOT NULL,
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

    deleted BOOLEAN DEFAULT false,
    deleted_at TIMESTAMP,

    -- 🔐 CONSTRAINTS DE NEGÓCIO
    CONSTRAINT ux_accounts_company_document UNIQUE (company_document),
    CONSTRAINT ux_accounts_company_email UNIQUE (company_email)
);
