-- V4__insert_accounts_super_admin.sql
SET search_path TO public;

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
    'admin@platform.com',
    '$2a$10$NHoV1pUU3gMGp87cYuvtReeq1iMqDOeHknZhrgzAcaygIVSuLFSQy',
    'SUPER_ADMIN',
    true,
    a.id
FROM accounts a
WHERE a.slug = 'platform'
AND NOT EXISTS (
    SELECT 1
    FROM users_account u
   WHERE u.account_id = a.id
    AND (u.username = 'superadmin' OR u.email = 'admin@platform.com')
);