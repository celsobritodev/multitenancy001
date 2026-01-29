-- V11__create_unique_taxid_per_country_not_deleted

CREATE TABLE public.account_provisioning_events (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    failure_code VARCHAR(50),
    message TEXT,
    details_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_account_prov_events_account_id
    ON public.account_provisioning_events(account_id);

CREATE INDEX idx_account_prov_events_created_at
    ON public.account_provisioning_events(created_at);
