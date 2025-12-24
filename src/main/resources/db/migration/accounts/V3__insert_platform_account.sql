-- V3__insert_platform_account.sql
SET search_path TO public;

INSERT INTO accounts (
    is_system_account,
    name,
    schema_name,
    slug,
    status,
    subscription_plan,
    company_document,
    company_email,
    company_country,
    deleted
)
SELECT
    true, -- 🔥 Marcada como conta do sistema
    'Platform',
    'public',
    'platform',
    'ACTIVE',
    'FREE',
    '00000000000000',
    'platform@system.com',
    'Brasil',
    false
WHERE NOT EXISTS (
    SELECT 1
    FROM accounts
    WHERE slug = 'platform'
);