-- V7__create_table_sales.sql



CREATE TABLE IF NOT EXISTS sales (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

  sale_date TIMESTAMP NOT NULL,
  total_amount NUMERIC(12,2) NOT NULL,

  customer_name VARCHAR(200),
  customer_document VARCHAR(20),
  customer_email VARCHAR(150),
  customer_phone VARCHAR(20),

  status VARCHAR(20) NOT NULL,

  created_at TIMESTAMP NOT NULL DEFAULT now(),
  updated_at TIMESTAMP,

  -- AUDITORIA (compatível com AuditInfo)
  created_by BIGINT,
  updated_by BIGINT,
  deleted_by BIGINT,

  created_by_username VARCHAR(120),
  updated_by_username VARCHAR(120),
  deleted_by_username VARCHAR(120)
);

CREATE INDEX IF NOT EXISTS idx_sales_sale_date ON sales(sale_date);
CREATE INDEX IF NOT EXISTS idx_sales_status ON sales(status);
