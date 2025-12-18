INSERT INTO users_account (
    name,
    username,
    email,
    password,
    role,
    active,
    account_id
)
SELECT
    'Platform Super Admin',
    'superadmin',
    'admin@plataforma.com',
    '$2a$10$wHq7p2n2YQhYF8k8y8y9xeJ6n9mT9KcXyZ1XxkYtYwHh6xXcYpK9S',
    'SUPER_ADMIN',
    true,
    1
WHERE NOT EXISTS (
    SELECT 1
    FROM users_account
    WHERE account_id = 1
      AND (username = 'superadmin' OR email = 'admin@plataforma.com')
);
