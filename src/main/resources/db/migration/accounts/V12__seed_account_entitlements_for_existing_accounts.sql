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
    CASE a.subscription_plan
        WHEN 'FREE' THEN 5
        WHEN 'PRO' THEN 20
        WHEN 'ENTERPRISE' THEN 999999
        ELSE 5
    END AS max_users,
    CASE a.subscription_plan
        WHEN 'FREE' THEN 100
        WHEN 'PRO' THEN 1000
        WHEN 'ENTERPRISE' THEN 999999
        ELSE 100
    END AS max_products,
    CASE a.subscription_plan
        WHEN 'FREE' THEN 100
        WHEN 'PRO' THEN 1024
        WHEN 'ENTERPRISE' THEN 999999
        ELSE 100
    END AS max_storage_mb,
    now(),
    now()
FROM accounts a
WHERE a.deleted = false
  AND a.account_type = 'TENANT'
  AND a.account_origin <> 'BUILT_IN'
ON CONFLICT (account_id) DO UPDATE SET
    max_users = EXCLUDED.max_users,
    max_products = EXCLUDED.max_products,
    max_storage_mb = EXCLUDED.max_storage_mb,
    updated_at = now();