
-- V3__insert_controlplane_account.sql
SET search_path TO public;

INSERT INTO accounts (
    is_system_account,
    name,
    schema_name,
    slug,
    status,
    subscription_plan,
    company_doc_type,
    company_doc_number,
    company_email,
    company_country,
    deleted
)
SELECT
    true,
    'ControlPlane',
    'public',
    'controlplane',
    'ACTIVE',
    'FREE',
    'CNPJ',
    '00000000000000',
    'controlplane@system.com',
    'Brasil',
    false
WHERE NOT EXISTS (
    SELECT 1
    FROM accounts
    WHERE slug = 'controlplane'
);
