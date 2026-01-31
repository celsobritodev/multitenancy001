-- V4__create_table_controlplane_users.sql
SET search_path TO public;

CREATE TABLE IF NOT EXISTS controlplane_users (
    id BIGSERIAL PRIMARY KEY,

    name VARCHAR(100) NOT NULL,

    -- ✅ email case-insensitive
    email CITEXT NOT NULL,
    password VARCHAR(255) NOT NULL,

    role VARCHAR(50) NOT NULL,
    account_id BIGINT NOT NULL,

    user_origin VARCHAR(20) NOT NULL DEFAULT 'ADMIN',

    suspended_by_account BOOLEAN NOT NULL DEFAULT FALSE,
    suspended_by_admin  BOOLEAN NOT NULL DEFAULT FALSE,

    last_login TIMESTAMPTZ,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    must_change_password BOOLEAN NOT NULL DEFAULT false,
    password_changed_at TIMESTAMPTZ,

    timezone VARCHAR(60) NOT NULL DEFAULT 'America/Sao_Paulo',
    locale VARCHAR(20) NOT NULL DEFAULT 'pt_BR',

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,

    created_by BIGINT,
    updated_by BIGINT,
    deleted_by BIGINT,

    created_by_email CITEXT,
    updated_by_email CITEXT,
    deleted_by_email CITEXT,

    deleted BOOLEAN NOT NULL DEFAULT false,

    password_reset_token VARCHAR(255),
    password_reset_expires TIMESTAMPTZ,

    phone VARCHAR(20),
    avatar_url VARCHAR(500),

    CONSTRAINT fk_controlplane_users_account
        FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE,

    CONSTRAINT chk_cp_user_origin
        CHECK (user_origin IN ('BUILT_IN', 'ADMIN', 'API'))
);

-- ✅ dentro de 1 conta, email único (CITEXT já resolve case-insensitive)
CREATE UNIQUE INDEX IF NOT EXISTS ux_cp_users_email_active
    ON controlplane_users (account_id, email)
    WHERE deleted = false;
