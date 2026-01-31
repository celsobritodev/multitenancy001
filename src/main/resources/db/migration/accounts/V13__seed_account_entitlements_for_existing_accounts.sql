-- V13__seed_account_entitlements_for_existing_accounts.sql
SET search_path TO public;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'account_entitlements'
    ) THEN
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
          AND NOT EXISTS (
              SELECT 1 FROM account_entitlements e WHERE e.account_id = a.id
          );
    END IF;
END $$;
