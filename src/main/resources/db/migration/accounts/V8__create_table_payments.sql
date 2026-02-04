-- V8__create_table_payments.sql
SET search_path TO public;

CREATE TABLE IF NOT EXISTS payments (
    id BIGSERIAL PRIMARY KEY,

    account_id BIGINT NOT NULL REFERENCES accounts(id),

    -- alinhado com Payment.java
    amount NUMERIC(14,2) NOT NULL,

    -- enums como STRING
    payment_method  VARCHAR(50) NOT NULL,
    payment_gateway VARCHAR(50) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    currency VARCHAR(3) NOT NULL DEFAULT 'BRL',

    transaction_id VARCHAR(100) UNIQUE,
    description VARCHAR(500),

    metadata_json TEXT,
    invoice_url   TEXT,
    receipt_url   TEXT,

    -- instantes reais (Instant <-> TIMESTAMPTZ)
    payment_date TIMESTAMPTZ NOT NULL,
    valid_until  TIMESTAMPTZ,
    refunded_at  TIMESTAMPTZ,

    refund_amount NUMERIC(14,2),
    refund_reason VARCHAR(500),

    -- auditoria (seu padrão AuditInfo)
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
CREATE INDEX IF NOT EXISTS idx_payments_status     ON payments (status);
CREATE INDEX IF NOT EXISTS idx_payments_payment_date ON payments (payment_date);
