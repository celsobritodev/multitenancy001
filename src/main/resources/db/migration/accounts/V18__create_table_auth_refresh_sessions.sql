-- ============================================================
-- V18__create_table_auth_refresh_sessions.sql
--
-- Tabela no PUBLIC schema para suportar:
-- - Refresh com rotação
-- - Logout forte (revogação server-side)
-- - Revogar “all devices”
--
-- Observações:
-- - refresh_token_hash guarda hash (não armazena o token puro)
-- - session_domain indica se a sessão é CONTROLPLANE ou TENANT
-- - tenant_schema é preenchido apenas para TENANT
-- ============================================================

SET search_path TO public;

CREATE TABLE IF NOT EXISTS auth_refresh_sessions (
    id                  UUID PRIMARY KEY,
    session_domain       VARCHAR(32) NOT NULL,     -- CONTROLPLANE | TENANT
    account_id           BIGINT NOT NULL,
    user_id              BIGINT NOT NULL,

    tenant_schema        VARCHAR(128),             -- NULL para CONTROLPLANE

    refresh_token_hash   VARCHAR(128) NOT NULL,    -- hash SHA-256 (hex) do refresh token

    created_at           TIMESTAMPTZ NOT NULL,
    last_used_at         TIMESTAMPTZ,
    rotated_at           TIMESTAMPTZ,
    revoked_at           TIMESTAMPTZ,

    created_request_id   UUID,
    last_request_id      UUID,

    created_ip           VARCHAR(64),
    last_ip              VARCHAR(64),

    created_user_agent   VARCHAR(512),
    last_user_agent      VARCHAR(512),

    revoked_reason_json  TEXT
);

-- 1 sessão “corrente” por refresh hash (hash deve ser único)
CREATE UNIQUE INDEX IF NOT EXISTS ux_auth_refresh_sessions_refresh_hash
    ON auth_refresh_sessions (refresh_token_hash);

-- consultas comuns
CREATE INDEX IF NOT EXISTS ix_auth_refresh_sessions_user_lookup
    ON auth_refresh_sessions (session_domain, account_id, user_id, revoked_at);

CREATE INDEX IF NOT EXISTS ix_auth_refresh_sessions_tenant_lookup
    ON auth_refresh_sessions (tenant_schema, account_id, user_id, revoked_at);

CREATE INDEX IF NOT EXISTS ix_auth_refresh_sessions_last_used
    ON auth_refresh_sessions (last_used_at);
