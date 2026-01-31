-- V3__create_table_account_entitlements.sql
SET search_path TO public;

CREATE TABLE IF NOT EXISTS account_entitlements (
    account_id      BIGINT PRIMARY KEY,
    max_users       INTEGER NOT NULL,
    max_products    INTEGER NOT NULL,
    max_storage_mb  INTEGER NOT NULL DEFAULT 100,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_account_entitlements_account'
    ) THEN
        ALTER TABLE account_entitlements
            ADD CONSTRAINT fk_account_entitlements_account
            FOREIGN KEY (account_id) REFERENCES accounts(id)
            ON DELETE CASCADE;
    END IF;
END $$;
