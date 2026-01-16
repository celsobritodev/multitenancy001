-- V3__create_table_controlplane_users.sql
SET search_path TO public;

CREATE TABLE IF NOT EXISTS controlplane_users (
    id BIGSERIAL PRIMARY KEY,

    name VARCHAR(100) NOT NULL,
    username VARCHAR(100) NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(150) NOT NULL,

    role VARCHAR(50) NOT NULL,

    account_id BIGINT NOT NULL,

    -- ✅ identifica usuários padrão do sistema (readonly)
    is_system_user BOOLEAN NOT NULL DEFAULT FALSE,

    suspended_by_account BOOLEAN NOT NULL DEFAULT FALSE,
    suspended_by_admin  BOOLEAN NOT NULL DEFAULT FALSE,

    last_login TIMESTAMP,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMP,
    must_change_password BOOLEAN NOT NULL DEFAULT false,
    password_changed_at TIMESTAMP,

    timezone VARCHAR(60) NOT NULL DEFAULT 'America/Sao_Paulo',
    locale VARCHAR(20) NOT NULL DEFAULT 'pt_BR',

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

    -- ✅ BLOQUEIO NO BANCO:
    -- impede que qualquer user NÃO-system use usernames reservados
    CONSTRAINT chk_cp_reserved_usernames_block
    CHECK (
        NOT (
            lower(username) IN ('superadmin','billing','support','operator')
            AND is_system_user = false
        )
    )
);

-- Unicidade por conta (apenas ativos)
CREATE UNIQUE INDEX IF NOT EXISTS ux_cp_users_username_active
ON controlplane_users (account_id, username)
WHERE deleted = false;

CREATE UNIQUE INDEX IF NOT EXISTS ux_cp_users_email_active
ON controlplane_users (account_id, email)
WHERE deleted = false;
