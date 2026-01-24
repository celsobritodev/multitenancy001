-- V6__insert_controlplane_users.sql
SET search_path TO public;

-- Cria 4 usuários padrão do Control Plane no account PLATFORM (slug controlplane)
-- Se já existirem (ativos) com o mesmo username/email, não duplica.

WITH cp_account AS (
    SELECT id
    FROM accounts
    WHERE slug = 'controlplane'
    LIMIT 1
)
INSERT INTO controlplane_users (
    name,
    username,
    email,
    password,
    role,
    account_id,
    user_origin,              -- ✅ novo
    suspended_by_account,
    suspended_by_admin,
    must_change_password
)
SELECT
    u.name,
    u.username,
    u.email,
    u.password,
    u.role,
    a.id,
    'BUILT_IN',               -- ✅ sempre BUILT_IN para esses 4
    false,
    false,
    u.must_change_password
FROM cp_account a
JOIN (
    VALUES
      -- OWNER (superadmin)
      ('ControlPlane Super Admin', 'superadmin', 'admin@controlplane.com',
       '$2a$10$NHoV1pUU3gMGp87cYuvtReeq1iMqDOeHknZhrgzAcaygIVSuLFSQy',
       'CONTROLPLANE_OWNER', false),

      -- BILLING MANAGER
      ('ControlPlane Billing Manager', 'billing', 'billing@controlplane.com',
       '$2a$10$NHoV1pUU3gMGp87cYuvtReeq1iMqDOeHknZhrgzAcaygIVSuLFSQy',
       'CONTROLPLANE_BILLING_MANAGER', true),

      -- SUPPORT
      ('ControlPlane Support', 'support', 'support@controlplane.com',
       '$2a$10$NHoV1pUU3gMGp87cYuvtReeq1iMqDOeHknZhrgzAcaygIVSuLFSQy',
       'CONTROLPLANE_SUPPORT', true),

      -- OPERATOR
      ('ControlPlane Operator', 'operator', 'operator@controlplane.com',
       '$2a$10$NHoV1pUU3gMGp87cYuvtReeq1iMqDOeHknZhrgzAcaygIVSuLFSQy',
       'CONTROLPLANE_OPERATOR', true)

) AS u(name, username, email, password, role, must_change_password)
ON TRUE
WHERE NOT EXISTS (
    SELECT 1
    FROM controlplane_users existing
    WHERE existing.account_id = a.id
      AND existing.deleted = false
      AND (existing.username = u.username OR existing.email = u.email)
);
