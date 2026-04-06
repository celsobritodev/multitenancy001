-- V20__create_table_account_usage_snapshots 
SET search_path TO public;



CREATE TABLE IF NOT EXISTS account_usage_snapshots (
    id BIGSERIAL PRIMARY KEY,

    account_id BIGINT NOT NULL UNIQUE REFERENCES accounts(id),

    current_users BIGINT NOT NULL DEFAULT 0,
    current_products BIGINT NOT NULL DEFAULT 0,
    current_storage_mb BIGINT NOT NULL DEFAULT 0,

    measured_at TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_account_usage_snapshots_measured_at
    ON account_usage_snapshots (measured_at);

CREATE INDEX IF NOT EXISTS idx_account_usage_snapshots_updated_at
    ON account_usage_snapshots (updated_at);