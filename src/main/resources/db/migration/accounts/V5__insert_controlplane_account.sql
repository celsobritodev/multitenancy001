-- V5__insert_controlplane_account.sql
SET search_path TO public;

INSERT INTO accounts (
    account_type,
    account_origin,
    display_name,
    legal_name,
    legal_entity_type,
    tenant_schema,
    slug,
    status,
    subscription_plan,
    tax_id_type,
    tax_id_number,
    tax_country_code,
    login_email,
    billing_email,
    country,
    timezone,
    locale,
    currency,
    deleted
)
SELECT
    'PLATFORM',
    'BUILT_IN',
    'Control Plane',
    NULL,
    'COMPANY',
    'public',
    'controlplane',
    'ACTIVE',
    'BUILT_IN_PLAN',
    'CNPJ',
    '00000000000000',
    'BR',
    'admin@controlplane.com',
    NULL,
    'Brasil',
    'America/Sao_Paulo',
    'pt_BR',
    'BRL',
    false
WHERE NOT EXISTS (
    SELECT 1 FROM accounts WHERE slug = 'controlplane'
);

