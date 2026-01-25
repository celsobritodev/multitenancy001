-- V9__crete_table_login_identities.sql
CREATE TABLE IF NOT EXISTS login_identities (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(150) NOT NULL,
    account_id BIGINT NOT NULL,
    user_type VARCHAR(30) NOT NULL, -- TENANT | CONTROLPLANE
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

-- Email único global = melhor UX (login sem escolher tenant)
CREATE UNIQUE INDEX IF NOT EXISTS uk_login_identities_email ON login_identities (email);

CREATE INDEX IF NOT EXISTS ix_login_identities_account ON login_identities (account_id);
