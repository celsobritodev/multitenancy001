INSERT INTO accounts (
    name,
    schema_name,
    slug,
    status
)
VALUES (
    'Plataforma',
    'public',
    'platform',
    'ACTIVE'
)
ON CONFLICT (slug) DO NOTHING;
