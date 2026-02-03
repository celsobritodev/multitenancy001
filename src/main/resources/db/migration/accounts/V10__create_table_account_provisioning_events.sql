-- V10__create_table_account_provisioning_events.sql
SET search_path TO public;

CREATE TABLE IF NOT EXISTS account_provisioning_events (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    failure_code VARCHAR(50),
    message TEXT,
    details_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_account_prov_events_account_id
    ON account_provisioning_events(account_id);

CREATE INDEX IF NOT EXISTS idx_account_prov_events_created_at
    ON account_provisioning_events(created_at);
