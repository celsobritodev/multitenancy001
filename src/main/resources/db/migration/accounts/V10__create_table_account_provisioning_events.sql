-- V10__create_table_account_provisioning_events.sql
SET search_path TO public;

CREATE TABLE IF NOT EXISTS public.account_provisioning_events (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    failure_code VARCHAR(50),
    message TEXT,
    details_json TEXT,
    created_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_account_prov_events_account
        FOREIGN KEY (account_id) REFERENCES public.accounts(id)
);

CREATE INDEX IF NOT EXISTS idx_account_prov_events_account_id
    ON public.account_provisioning_events (account_id);

CREATE INDEX IF NOT EXISTS idx_account_prov_events_created_at
    ON public.account_provisioning_events (created_at);

