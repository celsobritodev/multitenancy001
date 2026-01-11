-- V2__create_controlplane_users.sql
SET search_path TO public;

CREATE TABLE IF NOT EXISTS controlplane_users (
    id BIGSERIAL PRIMARY KEY,

    name VARCHAR(100) NOT NULL,
    username VARCHAR(100) NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(150) NOT NULL,

    role VARCHAR(50) NOT NULL,

    account_id BIGINT NOT NULL,

    suspended_by_account BOOLEAN NOT NULL DEFAULT FALSE,
    suspended_by_admin  BOOLEAN NOT NULL DEFAULT FALSE,

    last_login TIMESTAMP,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMP,
    must_change_password BOOLEAN NOT NULL DEFAULT false,
    password_changed_at TIMESTAMP,

    timezone VARCHAR(50) DEFAULT 'America/Sao_Paulo',
    locale VARCHAR(10) DEFAULT 'pt_BR',

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT false,

    password_reset_token VARCHAR(255),
    password_reset_expires TIMESTAMP,

    phone VARCHAR(20),
    avatar_url VARCHAR(500),

    CONSTRAINT fk_controlplane_users_account
        FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE,

    CONSTRAINT ux_controlplane_users_username UNIQUE (account_id, username),
    CONSTRAINT ux_controlplane_users_email UNIQUE (account_id, email)
);

