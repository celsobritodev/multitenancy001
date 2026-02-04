-- V8__create_table_payments.sql
SET search_path TO public;

CREATE TABLE IF NOT EXISTS payments (
    id BIGSERIAL PRIMARY KEY,

    account_id BIGINT NOT NULL REFERENCES accounts(id),

    amount NUMERIC(14,2) NOT NULL,

    payment_method  VARCHAR(30) NOT NULL,
    payment_gateway VARCHAR(30) NOT NULL,
    payment_status  VARCHAR(30) NOT NULL,

    description VARCHAR(500),

    -- instantes reais
    paid_at      TIMESTAMPTZ,
    valid_until  TIMESTAMPTZ,
    refunded_at  TIMESTAMPTZ,

    -- auditoria
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    created_by BIGINT,
    updated_by BIGINT,
    deleted_by BIGINT,

    created_by_email CITEXT,
    updated_by_email CITEXT,
    deleted_by_email CITEXT,

    deleted    BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_payments_account_id ON payments (account_id);
CREATE INDEX IF NOT EXISTS idx_payments_created_at ON payments (created_at);
CREATE INDEX IF NOT EXISTS idx_payments_paid_at ON payments (paid_at);

