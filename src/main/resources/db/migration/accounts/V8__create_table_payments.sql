-- V8__create_table_payments.sql
SET search_path TO public;

CREATE TABLE IF NOT EXISTS payments (
    id BIGSERIAL PRIMARY KEY,

    account_id BIGINT NOT NULL REFERENCES accounts(id),

    amount NUMERIC(14,2) NOT NULL,

    payment_method  VARCHAR(50) NOT NULL,
    payment_gateway VARCHAR(50) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    currency VARCHAR(3) NOT NULL DEFAULT 'BRL',

    transaction_id VARCHAR(100) UNIQUE,
    description VARCHAR(500),

    metadata_json TEXT,
    invoice_url   TEXT,
    receipt_url   TEXT,

    target_plan         VARCHAR(40),
    billing_cycle       VARCHAR(20),
    payment_purpose     VARCHAR(40) NOT NULL DEFAULT 'OTHER',
    plan_price_snapshot NUMERIC(14,2),
    effective_from      TIMESTAMPTZ,
    coverage_end_date   TIMESTAMPTZ,

    idempotency_key VARCHAR(160),

    payment_date TIMESTAMPTZ NOT NULL,
    valid_until  TIMESTAMPTZ,
    refunded_at  TIMESTAMPTZ,

    refund_amount NUMERIC(14,2),
    refund_reason VARCHAR(500),

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    created_by BIGINT,
    updated_by BIGINT,
    deleted_by BIGINT,

    created_by_email CITEXT,
    updated_by_email CITEXT,
    deleted_by_email CITEXT,

    deleted    BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMPTZ,

    CONSTRAINT uq_payments_transaction_id
        UNIQUE (transaction_id),

    CONSTRAINT uq_payments_idempotency_key
        UNIQUE (idempotency_key),

    CONSTRAINT chk_payments_amount_positive
        CHECK (amount > 0),

    CONSTRAINT chk_payments_currency_len
        CHECK (char_length(currency) = 3),

    CONSTRAINT chk_payments_idempotency_key_not_blank
        CHECK (
            idempotency_key IS NULL
            OR char_length(trim(idempotency_key)) > 0
        ),

    CONSTRAINT chk_payments_status_valid
        CHECK (
            status IN ('PENDING', 'COMPLETED', 'FAILED', 'REFUNDED', 'EXPIRED')
        ),

    CONSTRAINT chk_payments_purpose_valid
        CHECK (
            payment_purpose IN ('OTHER', 'PLAN_UPGRADE')
        ),

    CONSTRAINT chk_payments_billing_cycle_valid
        CHECK (
            billing_cycle IS NULL
            OR billing_cycle IN ('MONTHLY', 'YEARLY', 'ONE_TIME')
        ),

    CONSTRAINT chk_payments_refund_amount_non_negative
        CHECK (
            refund_amount IS NULL
            OR refund_amount >= 0
        ),

    CONSTRAINT chk_payments_refund_amount_not_greater_than_amount
        CHECK (
            refund_amount IS NULL
            OR refund_amount <= amount
        ),

    CONSTRAINT chk_payments_valid_until_consistency
        CHECK (
            valid_until IS NULL
            OR payment_date <= valid_until
        ),

    CONSTRAINT chk_payments_coverage_end_date_consistency
        CHECK (
            coverage_end_date IS NULL
            OR effective_from IS NULL
            OR effective_from <= coverage_end_date
        )
);

CREATE INDEX IF NOT EXISTS idx_payments_account_id
    ON payments (account_id);

CREATE INDEX IF NOT EXISTS idx_payments_status
    ON payments (status);

CREATE INDEX IF NOT EXISTS idx_payments_payment_date
    ON payments (payment_date);

CREATE INDEX IF NOT EXISTS idx_payments_target_plan
    ON payments (target_plan);

CREATE INDEX IF NOT EXISTS idx_payments_payment_purpose
    ON payments (payment_purpose);

CREATE INDEX IF NOT EXISTS idx_payments_idempotency_key
    ON payments (idempotency_key);

CREATE INDEX IF NOT EXISTS idx_payments_valid_until
    ON payments (valid_until);

CREATE INDEX IF NOT EXISTS idx_payments_account_status
    ON payments (account_id, status);