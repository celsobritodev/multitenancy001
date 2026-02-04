-- V14__create_table_auth_events.sql
SET search_path TO public;

CREATE TABLE IF NOT EXISTS auth_events (
    id BIGSERIAL PRIMARY KEY,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    request_id UUID,
    method TEXT,
    uri TEXT,

    ip INET,
    user_agent TEXT,

    auth_domain TEXT,
    event_type TEXT NOT NULL,
    outcome TEXT NOT NULL,

    principal_email CITEXT,
    principal_user_id BIGINT,

    account_id BIGINT,
    tenant_schema TEXT,

    details JSONB
);

CREATE INDEX IF NOT EXISTS idx_auth_events_occurred_at ON auth_events (occurred_at);
CREATE INDEX IF NOT EXISTS idx_auth_events_request_id ON auth_events (request_id);
CREATE INDEX IF NOT EXISTS idx_auth_events_email ON auth_events (principal_email);
CREATE INDEX IF NOT EXISTS idx_auth_events_account_id ON auth_events (account_id);
CREATE INDEX IF NOT EXISTS idx_auth_events_tenant_schema ON auth_events (tenant_schema);

