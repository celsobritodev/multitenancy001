SET search_path TO public;

INSERT INTO users (
    name,
    username,
    email,
    password,
    role,
    active,
    account_id
)
VALUES (
    'Platform Super Admin',
    'superadmin',
    'admin@plataforma.com',
    '$2a$10$wHq7p2n2YQhYF8k8y8y9xeJ6n9mT9KcXyZ1XxkYtYwHh6xXcYpK9S',
    'SUPER_ADMIN',
    true,
    1
)
ON CONFLICT DO NOTHING;
