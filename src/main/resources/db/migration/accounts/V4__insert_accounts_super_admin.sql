-- V4__insert_accounts_super_admin.sql
SET search_path TO public;

INSERT INTO users_account (
    name,
    username,
    email,
    password,
    role,
    account_id,
    suspended_by_account,
    suspended_by_admin
)
SELECT
    'Platform Super Admin',
    'superadmin',
    'admin@platform.com',
    '$2a$10$NHoV1pUU3gMGp87cYuvtReeq1iMqDOeHknZhrgzAcaygIVSuLFSQy',
    'SUPER_ADMIN',
    a.id,
    false,  -- não suspenso pela conta
    false   -- não suspenso pelo admin
FROM accounts a
WHERE a.slug = 'platform'
AND NOT EXISTS (
    SELECT 1
    FROM users_account u
    WHERE u.account_id = a.id
    AND (u.username = 'superadmin' OR u.email = 'admin@platform.com')
);