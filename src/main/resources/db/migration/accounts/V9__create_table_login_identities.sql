-- V9__create_table_login_identities.sql
SET search_path TO public;

CREATE TABLE IF NOT EXISTS login_identities (
    id BIGSERIAL PRIMARY KEY,

    email CITEXT NOT NULL,

    -- "SaaS moderno top": identity aponta para um subject (não para email na tabela do user)
    subject_type VARCHAR(40) NOT NULL,  -- 'CONTROLPLANE_USER' | 'TENANT_ACCOUNT'
    subject_id   BIGINT NOT NULL,

    -- mantém account_id para tenant (seu loginInit lista contas por email)
    account_id BIGINT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_login_identities_subject_type
        CHECK (subject_type IN ('CONTROLPLANE_USER', 'TENANT_ACCOUNT')),

    CONSTRAINT chk_login_identities_shape
        CHECK (
            -- CP: identity global (account_id NULL), subject_id = controlplane_users.id
            (subject_type = 'CONTROLPLANE_USER' AND account_id IS NULL)
            OR
            -- Tenant: identity por conta (account_id NOT NULL), subject_id = account_id (lookup)
            (subject_type = 'TENANT_ACCOUNT' AND account_id IS NOT NULL AND subject_id = account_id)
        ),

    CONSTRAINT fk_login_identities_account
        FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE
);

-- Unicidade CP por email (um email -> um CP user)
CREATE UNIQUE INDEX IF NOT EXISTS ux_login_identity_cp_email
    ON login_identities (email)
    WHERE subject_type = 'CONTROLPLANE_USER';

-- Unicidade CP por subject (um CP user -> um email identity)
CREATE UNIQUE INDEX IF NOT EXISTS ux_login_identity_cp_subject
    ON login_identities (subject_type, subject_id)
    WHERE subject_type = 'CONTROLPLANE_USER';

-- Tenant: mesmo email pode existir em várias contas
CREATE UNIQUE INDEX IF NOT EXISTS ux_login_identity_tenant_email_account
    ON login_identities (email, account_id)
    WHERE subject_type = 'TENANT_ACCOUNT';

CREATE INDEX IF NOT EXISTS idx_login_identities_email
    ON login_identities (email);

CREATE INDEX IF NOT EXISTS idx_login_identities_subject
    ON login_identities (subject_type, subject_id);
