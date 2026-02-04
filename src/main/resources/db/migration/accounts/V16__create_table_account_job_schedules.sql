-- V16__create_table_account_job_schedules.sql
SET search_path TO public;

CREATE TABLE IF NOT EXISTS account_job_schedules (
    id BIGSERIAL PRIMARY KEY,

    account_id BIGINT NOT NULL,
    job_key VARCHAR(80) NOT NULL,

    -- horário civil
    local_time TIME NOT NULL,
    -- timezone IANA (ex: America/Sao_Paulo)
    zone_id VARCHAR(60) NOT NULL,

    enabled BOOLEAN NOT NULL DEFAULT true,

    -- rastreabilidade e controle
    last_run_at TIMESTAMPTZ,
    next_run_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_account_job_schedules_account
        FOREIGN KEY (account_id) REFERENCES public.accounts(id),

    CONSTRAINT uq_account_job_schedules UNIQUE (account_id, job_key)
);

CREATE INDEX IF NOT EXISTS idx_account_job_schedules_next_run
    ON account_job_schedules (enabled, next_run_at);
