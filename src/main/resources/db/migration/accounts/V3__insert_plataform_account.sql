SET search_path TO public;

INSERT INTO accounts (
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
    'Plataforma',
    'public',
    'platform',
    'ACTIVE',
    'FREE',
    '00000000000000',
    'plataforma@sistema.com',
    'Brasil',
    false
WHERE NOT EXISTS (
    SELECT 1
    FROM accounts
    WHERE slug = 'platform'
);
