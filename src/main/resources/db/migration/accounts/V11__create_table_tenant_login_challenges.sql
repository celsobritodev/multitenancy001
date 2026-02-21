-- V11__create_table_tenant_login_challenges.sql
SET search_path TO public;

CREATE TABLE IF NOT EXISTS tenant_login_challenges (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    email CITEXT NOT NULL,

    -- CSV simples de accountIds válidos (ex: "1,2,3")
    candidate_account_ids TEXT NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_tenant_login_challenges_email
    ON tenant_login_challenges (email);

CREATE INDEX IF NOT EXISTS idx_tenant_login_challenges_expires_at
    ON tenant_login_challenges (expires_at);

CREATE INDEX IF NOT EXISTS idx_tenant_login_challenges_used_at
    ON tenant_login_challenges (used_at);

