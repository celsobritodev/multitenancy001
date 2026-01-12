-- V4__insert_controlplane_super_admin.sql
SET search_path TO public;

INSERT INTO controlplane_users (
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
    'ControlPlane Super Admin',
    'superadmin',
    'admin@controlplane.com',
    '$2a$10$NHoV1pUU3gMGp87cYuvtReeq1iMqDOeHknZhrgzAcaygIVSuLFSQy',
    'CONTROLPLANE_OWNER',
    a.id,
    false,
    false
FROM accounts a
WHERE a.slug = 'controlplane'
AND NOT EXISTS (
    SELECT 1
    FROM controlplane_users u
    WHERE u.account_id = a.id
      AND (u.username = 'superadmin' OR u.email = 'admin@controlplane.com')
);

