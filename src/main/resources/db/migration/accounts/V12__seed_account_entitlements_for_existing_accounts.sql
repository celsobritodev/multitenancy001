-- V12__seed_account_entitlements_for_existing_accounts.sql
SET search_path TO public;

INSERT INTO account_entitlements (
    account_id,
    max_users,
    max_products,
    max_storage_mb,
    created_at,
    updated_at
)
SELECT
    a.id,
    5,
    100,
    100,
    now(),
    now()
FROM accounts a
WHERE a.deleted = false
  AND a.account_type = 'TENANT'
  AND a.account_origin <> 'BUILT_IN'
ON CONFLICT (account_id) DO NOTHING;

