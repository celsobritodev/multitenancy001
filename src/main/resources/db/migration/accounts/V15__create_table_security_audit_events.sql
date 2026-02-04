-- V15__create_table_security_audit_events.sql
SET search_path TO public;

CREATE TABLE IF NOT EXISTS security_audit_events (
    id BIGSERIAL PRIMARY KEY,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    request_id UUID,
    method TEXT,
    uri TEXT,

    ip INET,
    user_agent TEXT,

    action_type TEXT NOT NULL,
    outcome TEXT NOT NULL,

    actor_email CITEXT,
    actor_user_id BIGINT,

    target_email CITEXT,
    target_user_id BIGINT,

    account_id BIGINT,
    tenant_schema TEXT,

    details JSONB
);

CREATE INDEX IF NOT EXISTS idx_security_audit_occurred_at ON security_audit_events (occurred_at);
CREATE INDEX IF NOT EXISTS idx_security_audit_request_id ON security_audit_events (request_id);
CREATE INDEX IF NOT EXISTS idx_security_audit_action_type ON security_audit_events (action_type);
CREATE INDEX IF NOT EXISTS idx_security_audit_account_id ON security_audit_events (account_id);

