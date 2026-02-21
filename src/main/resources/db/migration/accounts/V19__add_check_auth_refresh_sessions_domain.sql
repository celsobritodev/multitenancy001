-- src/main/resources/db/migration/accounts/V19__add_check_auth_refresh_sessions_domain.sql
-- ============================================================
-- Garante domínios válidos e consistência tenant_schema vs session_domain
-- ============================================================

SET search_path TO public;

ALTER TABLE auth_refresh_sessions
    ADD CONSTRAINT ck_auth_refresh_sessions_domain
        CHECK (session_domain IN ('CONTROLPLANE', 'TENANT'));

ALTER TABLE auth_refresh_sessions
    ADD CONSTRAINT ck_auth_refresh_sessions_tenant_schema_consistency
        CHECK (
            (session_domain = 'CONTROLPLANE' AND tenant_schema IS NULL)
            OR
            (session_domain = 'TENANT' AND tenant_schema IS NOT NULL AND length(trim(tenant_schema)) > 0)
        );