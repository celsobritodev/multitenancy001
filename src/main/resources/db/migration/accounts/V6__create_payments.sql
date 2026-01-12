-- V5__create_payments.sql
SET search_path TO public;

CREATE TABLE IF NOT EXISTS payments (
    id BIGSERIAL PRIMARY KEY,

    account_id BIGINT NOT NULL,
    amount NUMERIC(10,2) NOT NULL,

    payment_date TIMESTAMP NOT NULL,
    valid_until TIMESTAMP,

    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    transaction_id VARCHAR(100) UNIQUE,
    payment_method VARCHAR(50),
    payment_gateway VARCHAR(50),
    currency VARCHAR(3) NOT NULL DEFAULT 'BRL',

    description VARCHAR(500),
    metadata_json TEXT,

    invoice_url TEXT,
    receipt_url TEXT,

    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP,

    refunded_at TIMESTAMP,
    refund_amount NUMERIC(10,2),
    refund_reason VARCHAR(500),

    CONSTRAINT fk_payments_account
        FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_payment_account ON payments(account_id);
CREATE INDEX IF NOT EXISTS idx_payment_status ON payments(status);
CREATE UNIQUE INDEX IF NOT EXISTS idx_payment_transaction ON payments(transaction_id) WHERE transaction_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_payment_date ON payments(payment_date);
