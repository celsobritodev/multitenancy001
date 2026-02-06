-- V1__create_table_tenant_users.sql

CREATE TABLE IF NOT EXISTS tenant_users (
    id BIGSERIAL PRIMARY KEY,

    account_id BIGINT NOT NULL,

    name VARCHAR(100) NOT NULL,
    email CITEXT NOT NULL,
    password VARCHAR(200) NOT NULL,

    role VARCHAR(50) NOT NULL,

    phone VARCHAR(20) NULL,
    avatar_url VARCHAR(500) NULL,
    timezone VARCHAR(50) NULL,
    locale VARCHAR(10) NULL,

    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    origin VARCHAR(20) NOT NULL DEFAULT 'ADMIN',

    last_login TIMESTAMPTZ NULL,
    locked_until TIMESTAMPTZ NULL,
    password_changed_at TIMESTAMPTZ NULL,

    password_reset_token VARCHAR(200) NULL,
    password_reset_expires TIMESTAMPTZ NULL,

    suspended_by_account BOOLEAN NOT NULL DEFAULT FALSE,
    suspended_by_admin BOOLEAN NOT NULL DEFAULT FALSE,

    deleted BOOLEAN NOT NULL DEFAULT FALSE,

    -- AUDIT (fonte única)
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by BIGINT NULL,
    created_by_email CITEXT  NULL,

    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by BIGINT NULL,
    updated_by_email CITEXT  NULL,

    deleted_at TIMESTAMPTZ NULL,
    deleted_by BIGINT NULL,
    deleted_by_email CITEXT  NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_tenant_users_email_not_deleted
    ON tenant_users(email)
    WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS ix_tenant_users_account_id
    ON tenant_users(account_id);

CREATE INDEX IF NOT EXISTS ix_tenant_users_deleted
    ON tenant_users(deleted);

