-- V8__create_table_payments.sql
SET search_path TO public;

CREATE TABLE IF NOT EXISTS payments (
    id BIGSERIAL PRIMARY KEY,

    account_id BIGINT NOT NULL,
    amount NUMERIC(10,2) NOT NULL,

    payment_date TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ,

    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    transaction_id VARCHAR(100) UNIQUE,
    payment_method VARCHAR(50) NOT NULL,
    payment_gateway VARCHAR(50) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'BRL',

    description VARCHAR(500),
    metadata_json TEXT,

    invoice_url TEXT,
    receipt_url TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,

    created_by BIGINT,
    updated_by BIGINT,
    deleted_by BIGINT,

    created_by_email CITEXT,
    updated_by_email CITEXT,
    deleted_by_email CITEXT,

    refunded_at TIMESTAMPTZ,
    refund_amount NUMERIC(10,2),
    refund_reason VARCHAR(500),

    CONSTRAINT fk_payments_account
        FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_payment_account ON payments(account_id);
CREATE INDEX IF NOT EXISTS idx_payment_status  ON payments(status);
CREATE INDEX IF NOT EXISTS idx_payment_date    ON payments(payment_date);
