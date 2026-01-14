-- V4__insert_controlplane_account.sql
SET search_path TO public;

INSERT INTO accounts (
    account_type,
    name,
    schema_name,
    slug,
    status,
    subscription_plan,
    company_doc_type,
    company_doc_number,
    company_email,
    company_country,
    timezone,
    locale,
    currency,
    deleted
)
SELECT
    'SYSTEM',
    'Control Plane',
    'public',
    'controlplane',
    'ACTIVE',
    'SYSTEM',
    'CNPJ',
    '00000000000000',
    'admin@controlplane.com',
    'Brasil',
    'America/Sao_Paulo',
    'pt_BR',
    'BRL',
    false
WHERE NOT EXISTS (
    SELECT 1 FROM accounts WHERE slug = 'controlplane'
);
