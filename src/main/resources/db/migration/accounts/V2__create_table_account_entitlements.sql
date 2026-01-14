-- V2__create_table_account_entitlements.sql
SET search_path TO public;

CREATE TABLE IF NOT EXISTS account_entitlements (
  account_id BIGINT PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
  max_users INT NOT NULL,
  max_products INT NOT NULL,
  max_storage_mb INT NOT NULL
);

-- (opcional) coerência básica
ALTER TABLE account_entitlements
    ADD CONSTRAINT chk_entitlements_positive
    CHECK (max_users > 0 AND max_products > 0 AND max_storage_mb > 0);
