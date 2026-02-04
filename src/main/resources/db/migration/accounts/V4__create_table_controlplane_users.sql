-- V4__create_table_controlplane_users.sql
SET search_path TO public;

CREATE TABLE IF NOT EXISTS controlplane_users (
    id BIGSERIAL PRIMARY KEY,

    account_id BIGINT NOT NULL REFERENCES accounts(id),

    user_origin VARCHAR(20) NOT NULL DEFAULT 'ADMIN',

    name  VARCHAR(100) NOT NULL,
    email CITEXT NOT NULL,
    password VARCHAR(255) NOT NULL,

    role VARCHAR(50),

    suspended_by_account BOOLEAN NOT NULL DEFAULT false,
    suspended_by_admin   BOOLEAN NOT NULL DEFAULT false,

    -- auditoria (instantes reais)
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    created_by BIGINT,
    updated_by BIGINT,
    deleted_by BIGINT,

    created_by_email CITEXT,
    updated_by_email CITEXT,
    deleted_by_email CITEXT,

    deleted    BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_controlplane_users_email_active
    ON controlplane_users (email)
    WHERE deleted = false;

CREATE INDEX IF NOT EXISTS idx_controlplane_users_account_id ON controlplane_users (account_id);
CREATE INDEX IF NOT EXISTS idx_controlplane_users_created_at ON controlplane_users (created_at);

