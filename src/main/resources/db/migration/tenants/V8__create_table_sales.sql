-- V8__create_table_sales.sql

CREATE TABLE IF NOT EXISTS sales (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

  sale_date TIMESTAMPTZ NOT NULL,
  total_amount NUMERIC(12,2) NOT NULL,

  customer_id UUID,
  customer_name VARCHAR(200),
  customer_document VARCHAR(20),
  customer_email VARCHAR(150),
  customer_phone VARCHAR(20),

  status VARCHAR(20) NOT NULL,

  deleted BOOLEAN NOT NULL DEFAULT false,

  -- AUDIT (fonte única)
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by BIGINT,
  created_by_email CITEXT,

  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by BIGINT,
  updated_by_email CITEXT,

  deleted_at TIMESTAMPTZ,
  deleted_by BIGINT,
  deleted_by_email CITEXT,

  CONSTRAINT fk_sales_customer
    FOREIGN KEY (customer_id) REFERENCES customers(id)
);

CREATE INDEX IF NOT EXISTS idx_sales_sale_date ON sales(sale_date);
CREATE INDEX IF NOT EXISTS idx_sales_status ON sales(status);
CREATE INDEX IF NOT EXISTS idx_sales_customer_id ON sales(customer_id);
CREATE INDEX IF NOT EXISTS idx_sales_deleted ON sales(deleted) WHERE deleted = false;