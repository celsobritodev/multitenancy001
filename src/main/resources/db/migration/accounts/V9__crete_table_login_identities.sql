-- V9__crete_table_login_identities.sql
SET search_path TO public;

CREATE TABLE IF NOT EXISTS login_identities (
    id BIGSERIAL PRIMARY KEY,

    email VARCHAR(150) NOT NULL,

    user_type VARCHAR(20) NOT NULL, -- 'TENANT' | 'CONTROLPLANE'
    account_id BIGINT,              -- para TENANT: obrigatório / para CONTROLPLANE: pode ser NULL

    created_at TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT chk_login_identities_user_type
        CHECK (user_type IN ('TENANT', 'CONTROLPLANE')),

    CONSTRAINT fk_login_identities_account
        FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE
);

-- ✅ CONTROLPLANE: email único global (user_type='CONTROLPLANE')
CREATE UNIQUE INDEX IF NOT EXISTS ux_login_identity_cp_email
ON login_identities (email)
WHERE user_type = 'CONTROLPLANE';

-- ✅ TENANT: email pode repetir em vários tenants, mas não pode repetir no MESMO tenant
CREATE UNIQUE INDEX IF NOT EXISTS ux_login_identity_tenant_email_account
ON login_identities (email, account_id)
WHERE user_type = 'TENANT';

-- lookup por email (lista de tenants)
CREATE INDEX IF NOT EXISTS idx_login_identities_email_tenant
ON login_identities (email)
WHERE user_type = 'TENANT';
