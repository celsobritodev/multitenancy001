-- Garante que estamos no schema public
SET search_path TO public;

-- Cria usuário SUPER_ADMIN da plataforma
INSERT INTO users (
    name,
    username,
    email,
    password,
    role,
    active,
    created_at
)
VALUES (
    'Platform Super Admin',
    'superadmin',
    'admin@plataforma.com',
    '$2a$10$wHq7p2n2YQhYF8k8y8y9xeJ6n9mT9KcXyZ1XxkYtYwHh6xXcYpK9S', -- bcrypt
    'SUPER_ADMIN',
    true,
    now()
)
ON CONFLICT (username) DO NOTHING;
