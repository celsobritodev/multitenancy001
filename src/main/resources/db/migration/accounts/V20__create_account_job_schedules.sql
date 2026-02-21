-- =========================================================
-- Account Job Schedules (Control Plane)
-- =========================================================
-- Responsável por agendamentos internos do sistema
-- (jobs administrativos, billing, manutenção, etc.)
--
-- IMPORTANTE:
-- - Sempre criado no schema PUBLIC
-- - Nunca depende de tenant schema
-- =========================================================

CREATE TABLE IF NOT EXISTS public.account_job_schedules (
    id              BIGSERIAL PRIMARY KEY,

    account_id      BIGINT      NOT NULL,
    job_key         VARCHAR(100) NOT NULL,

    enabled         BOOLEAN     NOT NULL DEFAULT TRUE,

    local_time      TIME        NOT NULL,
    zone_id         VARCHAR(50) NOT NULL,

    next_run_at     TIMESTAMPTZ,
    last_run_at     TIMESTAMPTZ,

    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_account_job_schedules_enabled_next_run
    ON public.account_job_schedules (enabled, next_run_at);

CREATE INDEX IF NOT EXISTS idx_account_job_schedules_account
    ON public.account_job_schedules (account_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_account_job_schedules_account_job
    ON public.account_job_schedules (account_id, job_key);