INSERT INTO accounts (
    id,
    name,
    schema_name,
    slug,
    status
)
VALUES (
    1,
    'Plataforma',
    'public',
    'platform',
    'ACTIVE'
)
ON CONFLICT DO NOTHING;
