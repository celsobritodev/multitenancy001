-- V6__insert_controlplane_users.sql
SET search_path TO public;

WITH cp_account AS (
    SELECT id
    FROM accounts
    WHERE slug = 'controlplane'
    LIMIT 1
)
INSERT INTO controlplane_users (
    name,
    email,
    password,
    role,
    account_id,
    user_origin,
    suspended_by_account,
    suspended_by_admin,
    must_change_password
)
SELECT
    u.name,
    u.email,
    u.password,
    u.role,
    a.id,
    'BUILT_IN',
    false,
    false,
    u.must_change_password
FROM cp_account a
JOIN (
    VALUES
      ('ControlPlane Super Admin', 'superadmin@platform.local',
       '$2a$10$NHoV1pUU3gMGp87cYuvtReeq1iMqDOeHknZhrgzAcaygIVSuLFSQy',
       'CONTROLPLANE_OWNER', false),

      ('ControlPlane Billing Manager', 'billing@platform.local',
       '$2a$10$NHoV1pUU3gMGp87cYuvtReeq1iMqDOeHknZhrgzAcaygIVSuLFSQy',
       'CONTROLPLANE_BILLING_MANAGER', true),

      ('ControlPlane Support', 'support@platform.local',
       '$2a$10$NHoV1pUU3gMGp87cYuvtReeq1iMqDOeHknZhrgzAcaygIVSuLFSQy',
       'CONTROLPLANE_SUPPORT', true),

      ('ControlPlane Operator', 'operator@platform.local',
       '$2a$10$NHoV1pUU3gMGp87cYuvtReeq1iMqDOeHknZhrgzAcaygIVSuLFSQy',
       'CONTROLPLANE_OPERATOR', true)

) AS u(name, email, password, role, must_change_password)
ON TRUE
WHERE NOT EXISTS (
    SELECT 1
    FROM controlplane_users existing
    WHERE existing.account_id = a.id
      AND existing.deleted = false
      AND existing.email = u.email
);
