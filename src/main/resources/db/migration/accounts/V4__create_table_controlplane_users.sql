-- V4__create_table_controlplane_users.sql
SET search_path TO public;

CREATE TABLE IF NOT EXISTS controlplane_users (
    id BIGSERIAL PRIMARY KEY,

    account_id BIGINT NOT NULL REFERENCES accounts(id),

    user_origin VARCHAR(20) NOT NULL DEFAULT 'ADMIN',

    name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL,
    password VARCHAR(255) NOT NULL,

    role VARCHAR(50),

    -- AUTH / SECURITY (Instant <-> TIMESTAMPTZ)
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    last_login TIMESTAMPTZ NULL,
    locked_until TIMESTAMPTZ NULL,
    password_changed_at TIMESTAMPTZ NULL,
    password_reset_token VARCHAR(200) NULL,
    password_reset_expires TIMESTAMPTZ NULL,

    -- STATUS
    suspended_by_account BOOLEAN NOT NULL DEFAULT FALSE,
    suspended_by_admin BOOLEAN NOT NULL DEFAULT FALSE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,

    -- AUDIT (fonte única)
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(120),
    updated_at TIMESTAMPTZ,
    updated_by VARCHAR(120),
    deleted_at TIMESTAMPTZ,
    deleted_by VARCHAR(120)
);

-- email global (dentro do CP costuma ser global por conta; aqui seu repo também busca por account_id)
CREATE INDEX IF NOT EXISTS idx_cp_users_account_id ON controlplane_users(account_id);
CREATE INDEX IF NOT EXISTS idx_cp_users_email ON controlplane_users(email);

-- se quiser garantir unicidade por conta:
-- CREATE UNIQUE INDEX IF NOT EXISTS ux_cp_users_account_email ON controlplane_users(account_id, email);
